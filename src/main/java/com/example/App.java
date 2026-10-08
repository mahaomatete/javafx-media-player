package com.example;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class App extends Application {

    //Playlist data
    private final Map<String, List<File>> playlists = new LinkedHashMap<>();
    private String currentPlaylistName;
    private List<File> playlistFiles; // reference into playlists.get(currentPlaylistName)
    private final ObservableList<String> playlistNames = FXCollections.observableArrayList();

    //Play queue data 
    private final List<File> queueFiles = new ArrayList<>();
    private final ObservableList<String> queueNames = FXCollections.observableArrayList();

    //UI components
    private ComboBox<String> playlistSelector;
    private ListView<String> playlistView;
    private ListView<String> queueView;
    private MediaView mediaView;
    private MediaPlayer mediaPlayer;

    private int currentIndex = -1;   
    private double lastVolume = 0.5;
    private boolean muted = false;

    private Label statusLabel;
    private Slider volumeSlider;

    private Slider timelineSlider;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private boolean isSeeking = false;

    private static final Duration SEEK_STEP = Duration.seconds(10);

    @Override
    public void start(Stage stage) {
        // Start with one default playlist
        playlists.put("Default", new ArrayList<>());
        currentPlaylistName = "Default";
        playlistFiles = playlists.get(currentPlaylistName);

        //Header
        Label title = new Label("🎵 JavaFX Media Player");
        title.getStyleClass().add("header-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        //Media display area 
        mediaView = new MediaView();
        mediaView.setFitWidth(640);
        mediaView.setFitHeight(360);
        mediaView.setPreserveRatio(true);

        StackPane mediaPane = new StackPane(mediaView);
        mediaPane.getStyleClass().add("media-pane");
        mediaPane.setPrefSize(640, 360);

        //Timeline row 
        currentTimeLabel = new Label("00:00");
        totalTimeLabel = new Label("00:00");
        currentTimeLabel.getStyleClass().add("time-label");
        totalTimeLabel.getStyleClass().add("time-label");

        timelineSlider = new Slider(0, 1, 0);
        HBox.setHgrow(timelineSlider, Priority.ALWAYS);

        timelineSlider.setOnMousePressed(e -> isSeeking = true);
        timelineSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(timelineSlider.getValue()));
            }
            isSeeking = false;
        });

        HBox progressRow = new HBox(10, currentTimeLabel, timelineSlider, totalTimeLabel);
        progressRow.getStyleClass().add("progress-row");
        progressRow.setAlignment(Pos.CENTER);

        // Playback control buttons 
        Button playBtn = new Button("▶ Play");
        Button pauseBtn = new Button("⏸ Pause");
        Button stopBtn = new Button("⏹ Stop");
        Button backwardBtn = new Button("⏪ -10s");
        Button forwardBtn = new Button("⏩ +10s");
        Button prevBtn = new Button("⏮ Previous");
        Button nextBtn = new Button("⏭ Next");
        Button muteBtn = new Button("🔇 Mute");

        for (Button b : new Button[]{playBtn, pauseBtn, stopBtn, backwardBtn,
                forwardBtn, prevBtn, nextBtn, muteBtn}) {
            b.getStyleClass().add("control-button");
        }

        playBtn.setOnAction(e -> playCurrent());
        pauseBtn.setOnAction(e -> pauseCurrent());
        stopBtn.setOnAction(e -> stopCurrent());
        backwardBtn.setOnAction(e -> seekRelative(SEEK_STEP.negate()));
        forwardBtn.setOnAction(e -> seekRelative(SEEK_STEP));
        prevBtn.setOnAction(e -> playPrevious());
        nextBtn.setOnAction(e -> playNext());
        muteBtn.setOnAction(e -> toggleMute());

        volumeSlider = new Slider(0, 1, lastVolume);
        volumeSlider.setPrefWidth(120);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            lastVolume = newVal.doubleValue();
            if (mediaPlayer != null && !muted) {
                mediaPlayer.setVolume(lastVolume);
            }
        });

        Label volLabel = new Label("Vol:");
        volLabel.getStyleClass().add("vol-label");

        HBox controls = new HBox(10,
                playBtn, pauseBtn, stopBtn, backwardBtn, forwardBtn, prevBtn, nextBtn,
                volLabel, volumeSlider, muteBtn);
        controls.setPadding(new Insets(12));
        controls.setAlignment(Pos.CENTER);

        statusLabel = new Label("No file loaded. Press 'Add File' to load media.");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(0, 12, 12, 12));

        VBox bottomBox = new VBox(progressRow, controls, statusLabel);
        bottomBox.getStyleClass().add("bottom-bar");

        //Playlist selector row 
        playlistSelector = new ComboBox<>();
        playlistSelector.getItems().add(currentPlaylistName);
        playlistSelector.setValue(currentPlaylistName);
        playlistSelector.setPrefWidth(140);
        playlistSelector.setOnAction(e -> {
            String selected = playlistSelector.getValue();
            if (selected != null) {
                switchPlaylist(selected);
            }
        });

        Button newPlaylistBtn = new Button("+ New");
        Button deletePlaylistBtn = new Button("Delete");
        newPlaylistBtn.getStyleClass().addAll("control-button", "small-button");
        deletePlaylistBtn.getStyleClass().addAll("control-button", "small-button");
        newPlaylistBtn.setOnAction(e -> createNewPlaylist());
        deletePlaylistBtn.setOnAction(e -> deleteCurrentPlaylist());

        HBox selectorRow = new HBox(8, playlistSelector, newPlaylistBtn, deletePlaylistBtn);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.setPadding(new Insets(0, 0, 10, 0));

        // Playlist tab 
        playlistView = new ListView<>(playlistNames);
        playlistView.setPrefHeight(240);

        playlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = playlistView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    loadAndPlay(idx);
                }
            }
        });

        Button addBtn = new Button("+ Add File");
        Button removeBtn = new Button("Remove");
        Button addToQueueBtn = new Button("Add to Queue");
        addBtn.getStyleClass().addAll("control-button", "small-button");
        removeBtn.getStyleClass().addAll("control-button", "small-button");
        addToQueueBtn.getStyleClass().addAll("control-button", "small-button");

        addBtn.setOnAction(e -> addFile(stage));
        removeBtn.setOnAction(e -> removeSelected());
        addToQueueBtn.setOnAction(e -> addSelectedToQueue());

        HBox playlistButtons = new HBox(8, addBtn, removeBtn, addToQueueBtn);
        playlistButtons.setPadding(new Insets(10, 0, 0, 0));
        playlistButtons.setAlignment(Pos.CENTER);

        VBox playlistTabContent = new VBox(10, playlistView, playlistButtons);
        playlistTabContent.setPadding(new Insets(10));

        Tab playlistTab = new Tab("Playlist", playlistTabContent);
        playlistTab.setClosable(false);

        // Queue tab
        queueView = new ListView<>(queueNames);
        queueView.setPrefHeight(260);

        queueView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = queueView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    File f = queueFiles.remove(idx);
                    queueNames.remove(idx);
                    currentIndex = -1;
                    playlistView.getSelectionModel().clearSelection();
                    loadAndPlayFile(f);
                }
            }
        });

        Button removeFromQueueBtn = new Button("Remove");
        Button clearQueueBtn = new Button("Clear Queue");
        removeFromQueueBtn.getStyleClass().addAll("control-button", "small-button");
        clearQueueBtn.getStyleClass().addAll("control-button", "small-button");

        removeFromQueueBtn.setOnAction(e -> {
            int idx = queueView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                queueFiles.remove(idx);
                queueNames.remove(idx);
            }
        });
        clearQueueBtn.setOnAction(e -> {
            queueFiles.clear();
            queueNames.clear();
        });

        HBox queueButtons = new HBox(8, removeFromQueueBtn, clearQueueBtn);
        queueButtons.setPadding(new Insets(10, 0, 0, 0));
        queueButtons.setAlignment(Pos.CENTER);

        VBox queueTabContent = new VBox(10, queueView, queueButtons);
        queueTabContent.setPadding(new Insets(10));

        Tab queueTab = new Tab("Queue", queueTabContent);
        queueTab.setClosable(false);

        TabPane tabPane = new TabPane(playlistTab, queueTab);

        //Playlist panel 
        Label playlistLabel = new Label("Playlists");
        playlistLabel.getStyleClass().add("playlist-label");

        VBox playlistBox = new VBox(10, playlistLabel, selectorRow, tabPane);
        playlistBox.getStyleClass().add("playlist-box");
        playlistBox.setPadding(new Insets(15));
        playlistBox.setPrefWidth(270);

        // Layout 
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(mediaPane);
        root.setBottom(bottomBox);
        root.setRight(playlistBox);

        Scene scene = new Scene(root, 980, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        stage.setTitle("JavaFX Media Player");
        stage.setScene(scene);
        stage.show();

        root.requestFocus();
    }

    // Keyboard handling 
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case SPACE:
                togglePlayPause();
                event.consume();
                break;
            case S:
                stopCurrent();
                event.consume();
                break;
            case N:
                playNext();
                event.consume();
                break;
            case P:
                playPrevious();
                event.consume();
                break;
            case UP:
                changeVolume(0.05);
                event.consume();
                break;
            case DOWN:
                changeVolume(-0.05);
                event.consume();
                break;
            case M:
                toggleMute();
                event.consume();
                break;
            case RIGHT:
                seekRelative(SEEK_STEP);
                event.consume();
                break;
            case LEFT:
                seekRelative(SEEK_STEP.negate());
                event.consume();
                break;
            default:
                break;
        }
    }

    //Playlist (multi-playlist) management
    private void switchPlaylist(String name) {
        if (name == null || !playlists.containsKey(name)) {
            return;
        }
        currentPlaylistName = name;
        playlistFiles = playlists.get(name);

        playlistNames.clear();
        for (File f : playlistFiles) {
            playlistNames.add(f.getName());
        }

        currentIndex = -1;
        playlistView.getSelectionModel().clearSelection();
    }

    private void createNewPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Playlist");
        dialog.setHeaderText("Create a new playlist");
        dialog.setContentText("Playlist name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(rawName -> {
            String name = rawName.trim();
            if (name.isEmpty()) {
                showAlert("Playlist name cannot be empty.");
                return;
            }
            if (playlists.containsKey(name)) {
                showAlert("A playlist with that name already exists.");
                return;
            }
            playlists.put(name, new ArrayList<>());
            playlistSelector.getItems().add(name);
            playlistSelector.setValue(name); // triggers switchPlaylist via listener
        });
    }

    private void deleteCurrentPlaylist() {
        if (playlists.size() <= 1) {
            showAlert("You must keep at least one playlist.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete playlist \"" + currentPlaylistName + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Playlist");
        confirm.setHeaderText(null);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            String toRemove = currentPlaylistName;
            playlists.remove(toRemove);
            playlistSelector.getItems().remove(toRemove);
            String next = playlists.keySet().iterator().next();
            playlistSelector.setValue(next); // triggers switchPlaylist via listener
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void addFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Media File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files",
                        "*.mp4", "*.mp3", "*.m4a", "*.wav", "*.flv", "*.aiff"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            boolean wasEmpty = playlistFiles.isEmpty();
            playlistFiles.add(file);
            playlistNames.add(file.getName());

            // Auto-play only if nothing is playing at all and this playlist was empty
            if (wasEmpty && mediaPlayer == null) {
                loadAndPlay(0);
            }
        }
    }

    private void removeSelected() {
        int idx = playlistView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            return;
        }

        boolean removingCurrent = (idx == currentIndex);

        playlistFiles.remove(idx);
        playlistNames.remove(idx);

        if (removingCurrent) {
            stopCurrent();
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            currentIndex = -1;
            statusLabel.setText("No file loaded.");
            resetTimeline();
        } else if (idx < currentIndex) {
            currentIndex--;
        }
    }

    private void addSelectedToQueue() {
        int idx = playlistView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showAlert("Select a track in the playlist first.");
            return;
        }
        File f = playlistFiles.get(idx);
        queueFiles.add(f);
        queueNames.add(f.getName());
    }

    // Core playback logic 
    private void loadAndPlay(int index) {
        if (index < 0 || index >= playlistFiles.size()) {
            return;
        }
        currentIndex = index;
        playlistView.getSelectionModel().select(index);
        loadAndPlayFile(playlistFiles.get(index));
    }

    /** Loads and plays any file directly, regardless of playlist/queue origin. */
    private void loadAndPlayFile(File file) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setVolume(muted ? 0 : lastVolume);
        mediaView.setMediaPlayer(mediaPlayer);

        resetTimeline();

        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            timelineSlider.setMax(total.toSeconds());
            totalTimeLabel.setText(formatTime(total));
        });

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isSeeking) {
                timelineSlider.setValue(newTime.toSeconds());
            }
            currentTimeLabel.setText(formatTime(newTime));
        });

        mediaPlayer.setOnEndOfMedia(this::playNext);

        mediaPlayer.setOnError(() ->
                statusLabel.setText("Error playing: " + file.getName()
                        + " (" + mediaPlayer.getError() + ")"));

        mediaPlayer.play();
        statusLabel.setText("Now playing: " + file.getName());
    }

    private void playCurrent() {
        if (mediaPlayer == null && !playlistFiles.isEmpty()) {
            loadAndPlay(Math.max(currentIndex, 0));
        } else if (mediaPlayer != null) {
            mediaPlayer.play();
            statusLabel.setText("Resumed playback.");
        }
    }

    private void pauseCurrent() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            statusLabel.setText("Paused.");
        }
    }

    private void stopCurrent() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            statusLabel.setText("Stopped.");
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            playCurrent();
            return;
        }
        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            pauseCurrent();
        } else {
            playCurrent();
        }
    }

    /** Next always checks the play queue first, then falls back to the current playlist. */
    private void playNext() {
        if (!queueFiles.isEmpty()) {
            File f = queueFiles.remove(0);
            queueNames.remove(0);
            currentIndex = -1;
            playlistView.getSelectionModel().clearSelection();
            loadAndPlayFile(f);
            return;
        }

        if (playlistFiles.isEmpty()) {
            return;
        }
        int base = currentIndex < 0 ? -1 : currentIndex;
        int nextIndex = (base + 1) % playlistFiles.size();
        loadAndPlay(nextIndex);
    }

    /** Previous always operates on the current playlist */
    private void playPrevious() {
        if (playlistFiles.isEmpty()) {
            return;
        }
        int base = currentIndex < 0 ? 0 : currentIndex;
        int prevIndex = (base - 1 + playlistFiles.size()) % playlistFiles.size();
        loadAndPlay(prevIndex);
    }

    // Seeking / timeline
    private void seekRelative(Duration delta) {
        if (mediaPlayer == null) {
            return;
        }
        Duration current = mediaPlayer.getCurrentTime();
        Duration total = mediaPlayer.getTotalDuration();
        Duration target = current.add(delta);

        if (target.lessThan(Duration.ZERO)) {
            target = Duration.ZERO;
        } else if (total != null && target.greaterThan(total)) {
            target = total;
        }
        mediaPlayer.seek(target);
    }

    private void resetTimeline() {
        timelineSlider.setValue(0);
        timelineSlider.setMax(1);
        currentTimeLabel.setText("00:00");
        totalTimeLabel.setText("00:00");
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "00:00";
        }
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    //Volume / mute
    private void changeVolume(double delta) {
        if (muted) {
            return;
        }
        lastVolume = Math.max(0, Math.min(1, lastVolume + delta));
        volumeSlider.setValue(lastVolume);
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(lastVolume);
        }
    }

    private void toggleMute() {
        muted = !muted;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(muted ? 0 : lastVolume);
        }
        statusLabel.setText(muted ? "Muted" : "Unmuted");
    }

    public static void main(String[] args) {
        launch(args);
    }
}