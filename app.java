import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class app extends Application {
    private TournamentBracket tournament;
    private Team[] teams;
    private VBox participantsList;
    private VBox bracketView;
    private TextField bracketNameField;
    private ComboBox<String> bracketTypeCombo;
    private TextField sportField;
    private TextArea descriptionArea;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Label progressLabel;
    private CheckBox selectAllCheckBox;
    private double savedScrollH = 0;
    private double savedScrollV = 0;
    private int dragSourceIndex = -1;

     private static class MatchSnapshot {
        final int    matchId;
        final String winnerId;
        final String score;

    MatchSnapshot(int matchId, String winnerId, String score) {
        this.matchId  = matchId;
        this.winnerId = winnerId;
        this.score    = score;
    }
}

    private final Deque<List<MatchSnapshot>> undoStack = new ArrayDeque<>();

    private void pushUndoState() {
        if (tournament == null) return;
        List<MatchSnapshot> frame = new ArrayList<>();
        for (Match m : tournament.getAllMatches()) {
            String winnerName = (m.getWinner() != null) ? m.getWinner().getName() : null;
            frame.add(new MatchSnapshot(m.getMatchId(), winnerName, m.getScore()));
        }
        undoStack.push(frame);
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            showAlert("Undo", "Nothing to undo.");
            return;
        }
        List<MatchSnapshot> frame = undoStack.pop();
        Map<Integer, MatchSnapshot> snapMap = new HashMap<>();
        for (MatchSnapshot s : frame) snapMap.put(s.matchId, s);

        for (Match m : tournament.getAllMatches()) {
            MatchSnapshot s = snapMap.get(m.getMatchId());
            if (s == null) continue;
            tournament.revertMatch(m, s.winnerId, s.score);
        }

        updateBracketView();
        updateProgress();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Intramural Sports Bracket Generator");

        teams = new Team[0];
        tournament = null;

        VBox leftPanel   = createParticipantsPanel();
        VBox centerPanel = createBracketViewPanel();
        VBox rightPanel  = createInformationPanel();
        HBox bottomPanel = createBottomProgressPanel();

        HBox contentArea = new HBox(15, leftPanel, centerPanel, rightPanel);
        contentArea.setPadding(new Insets(15));
        contentArea.setFillHeight(true);
        HBox.setHgrow(centerPanel, Priority.ALWAYS);

        leftPanel.setPrefWidth(260);
        centerPanel.setPrefWidth(500);
        rightPanel.setPrefWidth(260);
        leftPanel.setMaxWidth(260);
        rightPanel.setMaxWidth(260);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setCenter(contentArea);
        mainLayout.setBottom(bottomPanel);
        mainLayout.setTop(createHeaderBar());
        mainLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainLayout.setStyle("-fx-background-color: linear-gradient(to right, #040D43 0%, #040D43 50%, #FFBA09 100%);");

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.setFill(Color.web("#040D43"));
        primaryStage.setScene(scene);
        primaryStage.show();

        updateBracketView();
        updateProgress();
    }

    // ==========================================================
    // HEADER
    // ==========================================================

    private HBox createHeaderBar() {
        HBox header = new HBox();
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: linear-gradient(to right, #040D43 0%, #040D43 50%, #FFBA09 100%); -fx-border-width: 1 0 0 0;");
        Label title = new Label("🏆 INTRAMURAL BRACKET GENERATOR"); 

        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#FFD862"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        header.getChildren().addAll(title, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ===========================================================
    // PARTICIPANTS PANEL
    // ===========================================================

    private VBox createParticipantsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #040D43; -fx-border-color: #7F8EE3; -fx-border-radius: 5; -fx-background-radius: 5;");

        Label title = new Label("PARTICIPANTS");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#FFD862"));

        selectAllCheckBox = new CheckBox("Select All");
        selectAllCheckBox.setStyle("-fx-font-size: 11px; -fx-text-fill: #E0E6ED;");
        selectAllCheckBox.setOnAction(e -> {
            boolean checked = selectAllCheckBox.isSelected();
            for (javafx.scene.Node node : participantsList.getChildren()) {
                if (node instanceof HBox) {
                    for (javafx.scene.Node child : ((HBox) node).getChildren()) {
                        if (child instanceof CheckBox) {
                            ((CheckBox) child).setSelected(checked);
                        }
                    }
                }
            }
        });


        participantsList = new VBox(3);
        participantsList.setPadding(new Insets(4, 4, 4, 4));  // was (6,6,0,6) — add bottom padding back
        participantsList.setStyle("-fx-background-color: #152055;");  // ADD this line

        ScrollPane listScroll = new ScrollPane(participantsList);
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScroll.setStyle(
        "-fx-background: #152055;" +
        "-fx-background-color: #152055;" +
        "-fx-border-color: transparent;");
        listScroll.setBackground(javafx.scene.layout.Background.EMPTY);
        listScroll.setFocusTraversable(false);
        listScroll.skinProperty().addListener((obs, oldSkin, newSkin) -> {
        listScroll.lookupAll(".scroll-bar").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        listScroll.lookupAll(".thumb").forEach(n ->
            n.setStyle("-fx-background-color: #7F8EE3; -fx-background-radius: 4;"));
        listScroll.lookupAll(".track").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        listScroll.lookupAll(".increment-button, .decrement-button").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        listScroll.lookupAll(".increment-arrow, .decrement-arrow").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        // Kill the focus ring on the inner viewport node
        javafx.scene.Node vp = listScroll.lookup(".viewport");
        if (vp != null) {
            vp.setStyle("-fx-background-color: #152055; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            vp.focusedProperty().addListener((fo, wf, nf) ->
                vp.setStyle("-fx-background-color: #152055; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"));
            }
        });
    
        VBox.setVgrow(listScroll, Priority.ALWAYS);

    
        Label inputPrompt = new Label("Enter participant name...");
        inputPrompt.setStyle("-fx-text-fill: #4a5a8a; -fx-font-size: 11px; -fx-font-style: italic;");
        inputPrompt.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(inputPrompt, Priority.ALWAYS);

        Label inputTyping = new Label();
        inputTyping.setStyle("-fx-text-fill: #E0E6ED; -fx-font-size: 11px;");
        inputTyping.setMaxWidth(Double.MAX_VALUE);
        inputTyping.setVisible(false);
        inputTyping.setManaged(false);

    
        Label cursor = new Label("|");
        cursor.setStyle("-fx-text-fill: #FFBA09; -fx-font-size: 11px; -fx-font-weight: bold;");
        cursor.setVisible(false);
        cursor.setManaged(false);

        HBox inputLine = new HBox(2, inputPrompt, inputTyping, cursor);
        inputLine.setAlignment(Pos.CENTER_LEFT);
        inputLine.setPadding(new Insets(6, 8, 6, 8));
        inputLine.setStyle(
            "-fx-background-color: #0d1a40;" +
            "-fx-border-color: #7F8EE3;" +
            "-fx-border-width: 1 0 0 0;" );
        inputLine.setMaxWidth(Double.MAX_VALUE);

    
        TextField hiddenField = new TextField();
        hiddenField.setMaxSize(0, 0);
        hiddenField.setMinSize(0, 0);
        hiddenField.setPrefSize(0, 0);
        hiddenField.setOpacity(0);
        hiddenField.setFocusTraversable(true);

        VBox innerStack = new VBox(listScroll, inputLine);
        innerStack.setPadding(new Insets(0));
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        StackPane inputBox = new StackPane(innerStack, hiddenField);
        inputBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        inputBox.setPrefHeight(380);
        inputBox.setStyle(
            "-fx-background-color: #152055;" +
            "-fx-border-color: #7F8EE3;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: text;"      );
        inputBox.setOnMouseClicked(e -> hiddenField.requestFocus());

        hiddenField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
        if (isFocused) {
            inputBox.setStyle(
                "-fx-background-color: #152055;" +
                "-fx-border-color: #7F8EE3;" +
                "-fx-border-width: 2;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: text;"  );
            
            cursor.setVisible(true);
            cursor.setManaged(true);
        } else {
            inputBox.setStyle(
                "-fx-background-color: #152055;" +
                "-fx-border-color: #7F8EE3;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: text;"
            );
            cursor.setVisible(false);
            cursor.setManaged(false);
            }
        });

   
    final StringBuilder typingBuffer = new StringBuilder();
    hiddenField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
        if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
            String name = typingBuffer.toString().trim();
            if (!name.isEmpty()) {
                if (teams.length >= 32) {
                    showAlert("Team Limit Reached", "Maximum of 32 teams allowed.");
                } else {
                    Team[] newTeams = new Team[teams.length + 1];
                    System.arraycopy(teams, 0, newTeams, 0, teams.length);
                    newTeams[teams.length] = new Team(teams.length, name);
                    teams = newTeams;
                    typingBuffer.setLength(0);
                    // Reset input line
                    inputTyping.setText("");
                    inputTyping.setVisible(false);
                    inputTyping.setManaged(false);
                    inputPrompt.setVisible(true);
                    inputPrompt.setManaged(true);
                    rebuildTournament();
                    updateParticipantsList();
                    updateBracketView();
                    updateProgress();
                }
            }
            e.consume();

        } else if (e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
            if (typingBuffer.length() > 0) {
                typingBuffer.deleteCharAt(typingBuffer.length() - 1);
                inputTyping.setText(typingBuffer.toString());
                if (typingBuffer.length() == 0) {
                    inputTyping.setVisible(false);
                    inputTyping.setManaged(false);
                    inputPrompt.setVisible(true);
                    inputPrompt.setManaged(true);
                }
            }
            e.consume();
        }
    });

    hiddenField.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
        String ch = e.getCharacter();
        if (ch == null || ch.isEmpty() || ch.charAt(0) < 32) return;
        if (typingBuffer.length() < 45) {
            typingBuffer.append(ch);
            inputTyping.setText(typingBuffer.toString());
            inputTyping.setVisible(true);
            inputTyping.setManaged(true);
            inputPrompt.setVisible(false);
            inputPrompt.setManaged(false);
         }
        e.consume();
    });

    Button removeTeamBtn = createStyledButton("REMOVE",  "#e74c3c");
        Button shuffleBtn    = createStyledButton("SHUFFLE", "#7F8EE3");
        Button undoBtn       = createStyledButton("UNDO",    "#7F8EE3");

        String remNormal = "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String remHover  = "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String shuNormal = "-fx-background-color: #7F8EE3; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String shuHover  = "-fx-background-color: #5a6abf; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String undNormal = "-fx-background-color: #7F8EE3; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String undHover  = "-fx-background-color: #5a6abf; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";

        removeTeamBtn.setStyle(remNormal);
        shuffleBtn.setStyle(shuNormal);
        undoBtn.setStyle(undNormal);

        removeTeamBtn.setOnMouseEntered(e -> removeTeamBtn.setStyle(remHover));
        removeTeamBtn.setOnMouseExited(e  -> removeTeamBtn.setStyle(remNormal));
        shuffleBtn.setOnMouseEntered(e -> shuffleBtn.setStyle(shuHover));
        shuffleBtn.setOnMouseExited(e  -> shuffleBtn.setStyle(shuNormal));
        undoBtn.setOnMouseEntered(e -> undoBtn.setStyle(undHover));
        undoBtn.setOnMouseExited(e  -> undoBtn.setStyle(undNormal));

        removeTeamBtn.setOnAction(e -> removeSelectedTeams());
        shuffleBtn.setOnAction(e    -> shuffleTeams());
        undoBtn.setOnAction(e       -> performUndo());

        Button simulateBtn = createStyledButton("SIMULATE", "#FFBA09");
        String simNormal = "-fx-background-color: #FFBA09; -fx-text-fill: #040D43; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String simHover  = "-fx-background-color: #e0a500; -fx-text-fill: #040D43; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        simulateBtn.setStyle(simNormal);
        simulateBtn.setOnMouseEntered(e -> simulateBtn.setStyle(simHover));
        simulateBtn.setOnMouseExited(e  -> simulateBtn.setStyle(simNormal));
        simulateBtn.setOnAction(e -> simulateBracket());

        removeTeamBtn.setPrefWidth(200);
        shuffleBtn.setPrefWidth(200);
        undoBtn.setPrefWidth(200);
        simulateBtn.setPrefWidth(200);

        HBox removeBox  = new HBox(removeTeamBtn);  removeBox.setAlignment(Pos.CENTER);
        HBox shuffleBox = new HBox(shuffleBtn);      shuffleBox.setAlignment(Pos.CENTER);
        HBox undoBox    = new HBox(undoBtn);         undoBox.setAlignment(Pos.CENTER);
        HBox simBox     = new HBox(simulateBtn);     simBox.setAlignment(Pos.CENTER);

        Label tip = new Label("Quick Tip: You can hold and drag participants to reorder them.");
        tip.setFont(Font.font("Arial", 10));
        tip.setTextFill(Color.web("#E0E6ED"));
        tip.setWrapText(true);

        panel.getChildren().addAll(title, selectAllCheckBox, inputBox, removeBox, shuffleBox, undoBox, simBox, tip);
        return panel;
    }

    // ── Drop-in replacement for updateParticipantsList() ─────────────────
    private void updateParticipantsList() {
        participantsList.getChildren().clear();
        if (selectAllCheckBox != null) selectAllCheckBox.setSelected(false);
        if (teams.length == 0) return;

        for (int idx = 0; idx < teams.length; idx++) {
        final int i = idx;
        Team team = teams[i];

        Label numLabel = new Label((i + 1) + ".");
        numLabel.setStyle("-fx-text-fill: #FFBA09; -fx-font-size: 11px;");
        numLabel.setMinWidth(16);
        numLabel.setPrefWidth(16);
        numLabel.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(team.getName());
        nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #E0E6ED;");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        CheckBox cb = new CheckBox();
        cb.setUserData(team);
        cb.setMinWidth(16);
        cb.setPrefWidth(16);
        cb.setMaxWidth(16);

        HBox row = new HBox(2);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 8, 3, 4));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-background-color: transparent; -fx-background-radius: 4; -fx-cursor: grab;");
        row.getChildren().addAll(numLabel, nameLabel, cb);  

        row.setOnMouseEntered(e -> {
            if (dragSourceIndex == -1)
                row.setStyle("-fx-background-color: #1e3060; -fx-background-radius: 4; -fx-cursor: grab;");
        });
        row.setOnMouseExited(e -> {
            if (dragSourceIndex == -1)
                row.setStyle("-fx-background-color: transparent; -fx-background-radius: 4; -fx-cursor: grab;");
        });

        row.setOnMousePressed(e -> {
            dragSourceIndex = i;
            row.setStyle("-fx-background-color: #1a2a5a ; -fx-background-radius: 4; -fx-border-color: #7F8EE3 ; -fx-border-radius: 4; -fx-cursor: grabbing;");
            e.consume();
            
        });

        row.setOnMouseDragged(e -> {
            double localY = participantsList.sceneToLocal(e.getSceneX(), e.getSceneY()).getY();
            int targetIdx = -1;
            double accY = 0;
            for (int k = 0; k < participantsList.getChildren().size(); k++) {
                javafx.scene.Node node = participantsList.getChildren().get(k);
                double nodeH = node.getBoundsInLocal().getHeight() + 3;
                if (localY >= accY && localY <= accY + nodeH) {
                    targetIdx = k;
                    break;
                }
                accY += nodeH;
            }
            for (int k = 0; k < participantsList.getChildren().size(); k++) {
                javafx.scene.Node node = participantsList.getChildren().get(k);
                if (node instanceof HBox) {
                    if (k == targetIdx && k != dragSourceIndex) {
                        ((HBox) node).setStyle(
                            "-fx-background-color: #cad4e4;" +
                            "-fx-background-radius: 4;" +
                            "-fx-border-color: #FFBA09;" +
                            "-fx-border-width: 0 0 2 0;");
                    } else if (k != dragSourceIndex) {
                        ((HBox) node).setStyle(
                            "-fx-background-color: transparent;" +
                            "-fx-background-radius: 4;");
                    }
                }
            }
            e.consume();
        });

        row.setOnMouseReleased(e -> {
            double localY = participantsList.sceneToLocal(e.getSceneX(), e.getSceneY()).getY();
            int targetIdx = dragSourceIndex;
            double accY = 0;
            for (int k = 0; k < participantsList.getChildren().size(); k++) {
                javafx.scene.Node node = participantsList.getChildren().get(k);
                double nodeH = node.getBoundsInLocal().getHeight() + 3;
                if (localY >= accY && localY <= accY + nodeH) {
                    targetIdx = k;
                    break;
                }
                accY += nodeH;
            }
            if (targetIdx != dragSourceIndex && targetIdx >= 0 && targetIdx < teams.length) {
                Team temp = teams[dragSourceIndex];
                teams[dragSourceIndex] = teams[targetIdx];
                teams[targetIdx] = temp;
                for (int k = 0; k < teams.length; k++)
                    teams[k] = new Team(k, teams[k].getName());
                rebuildTournament();
                updateParticipantsList();
                updateBracketView();
                updateProgress();
            } else {
                updateParticipantsList();
            }
            dragSourceIndex = -1;
            e.consume();
        });

        participantsList.getChildren().add(row);
        }
    }

    private void simulateBracket() {
        if (tournament == null) {
            showAlert("No Bracket", "Please set up a valid bracket first.");
            return;
        }
        java.util.Random rng = new java.util.Random();
        List<Match> pending = new ArrayList<>();
        for (Match m : tournament.getAllMatches()) {
            if (!m.isCompleted() && m.getTeam1() != null && m.getTeam2() != null
                    && !m.getTeam1().getName().equals("TBD")
                    && !m.getTeam2().getName().equals("TBD")) {
                pending.add(m);
            }
        }
        if (pending.isEmpty()) {
            showAlert("Simulate", "No pending matches to simulate.");
            return;
        }
        for (Match m : pending) {
            int s1, s2;
            do {
                s1 = rng.nextInt(21);
                s2 = rng.nextInt(21);
            } while (s1 == s2);
            Team winner = s1 > s2 ? m.getTeam1() : m.getTeam2();
            tournament.recordWinner(m, winner, s1, s2);
        }
        updateBracketView();
        updateProgress();
    }

    private VBox createBracketViewPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #040D43; -fx-border-color: #7F8EE3; -fx-border-radius: 5; -fx-background-radius: 5;");

        Label title = new Label("BRACKET VIEW");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#FFD862"));

        bracketView = new VBox(0);
        bracketView.setPadding(new Insets(0));
        bracketView.setFocusTraversable(false);

        panel.getChildren().addAll(title, bracketView);
        VBox.setVgrow(bracketView, Priority.ALWAYS);
        return panel;
    }

    // =========================================================================
    // ADD / REMOVE TEAMS
    // =========================================================================

    private String resolveActualType(String selectedType, int teamCount) {
        if ("Single Elimination".equals(selectedType) && teamCount == 12) {
            return "Single Elimination";
        }
        if ("Double Elimination".equals(selectedType) && teamCount == 12) {
            return "Double Elimination";
        }
        if ("Double Elimination".equals(selectedType) && !isPowerOfTwo(teamCount) && teamCount != 24) {
            return "Play-In Double Elimination";
        }
        return selectedType;
    }

    private boolean isValidTeamCount(int n, String bracketType) {
        if (n == 0) return false;
        switch (bracketType) {
            case "Single Elimination":
                return n == 4 || n == 8 || n == 12 || n == 16 || n == 24 || n == 32;
            case "Double Elimination":
                return (isPowerOfTwo(n) && n >= 4 && n <= 32) || n == 24 || n == 12;
            case "Round Robin":
                return n >= 4;
            case "Free For All":
                return n >= 4;
            default:
                return n >= 4;
        }
    }

     private void rebuildTournament() {
        undoStack.clear();
        for (int i = 0; i < teams.length; i++)
            teams[i] = new Team(teams[i].getId(), teams[i].getName());
        String btype = bracketTypeCombo.getValue();
        if (isValidTeamCount(teams.length, btype)) {
            tournament = new TournamentBracket(teams, resolveActualType(btype, teams.length));
        } else {
            tournament = null;
        }
    }

    private void removeSelectedTeams() {
        List<Team> teamsToRemove = new ArrayList<>();

        for (javafx.scene.Node node : participantsList.getChildren()) {
            CheckBox cb = null;
            if (node instanceof HBox) {
                for (javafx.scene.Node child : ((HBox) node).getChildren())
                    if (child instanceof CheckBox) { cb = (CheckBox) child; break; }
            } else if (node instanceof CheckBox) {
                cb = (CheckBox) node;
            }
            if (cb != null && cb.isSelected() && cb.getUserData() instanceof Team)
                teamsToRemove.add((Team) cb.getUserData());
        }

        if (teamsToRemove.isEmpty()) {
            showAlert("No Selection", "Please check the box next to the team(s) you want to remove.");
            return;
        }

        List<Team> remaining = new ArrayList<>();
        for (Team t : teams) {
            boolean remove = false;
            for (Team rt : teamsToRemove) if (t.getId() == rt.getId()) { remove = true; break; }
            if (!remove) remaining.add(t);
        }

        List<Team> reindexed = new ArrayList<>();
        for (int i = 0; i < remaining.size(); i++)
            reindexed.add(new Team(i, remaining.get(i).getName()));
        teams = reindexed.toArray(new Team[0]);

        rebuildTournament();
        updateParticipantsList();
        updateBracketView();
        updateProgress();

        String msg = teamsToRemove.size() + " team(s) removed. Remaining: " + teams.length;
        if (!isValidTeamCount(teams.length, bracketTypeCombo.getValue()) && teams.length > 0)
            msg += "\n" + getNotReadyMessage(teams.length, bracketTypeCombo.getValue());
        showAlert("Teams Removed", msg);
    }

    private void shuffleTeams() {
        if (teams.length < 2) {
            showAlert("Shuffle", "Add at least 2 teams before shuffling.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (Team t : teams) names.add(t.getName());
        Collections.shuffle(names);
        teams = new Team[names.size()];
        for (int i = 0; i < names.size(); i++) teams[i] = new Team(i, names.get(i));

        rebuildTournament();
        updateParticipantsList();
        updateBracketView();
        updateProgress();
    }

    // =========================================================================
    // INFORMATION PANEL
    // =========================================================================

    private VBox createInformationPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #040D43; -fx-border-color: #7F8EE3; -fx-border-radius: 5; -fx-background-radius: 5;");

        Label title = new Label("BRACKET INFORMATION");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#FFD862"));

        Label nameLabel = new Label("Bracket Name:");
        nameLabel.setTextFill(Color.web("#FFFFFF"));
        bracketNameField = new TextField("");
        bracketNameField.setPromptText("Enter bracket name...");
        bracketNameField.setStyle("-fx-background-color: #152055; -fx-text-fill: #E0E6ED; -fx-border-color: #7F8EE3; -fx-border-radius: 3;");
        bracketNameField.textProperty().addListener((obs, oldText, newText) -> {
        String[] lines = newText.split("\n", -1);
        int maxLines = 1;
        int maxCharsPerLine = 45;
        boolean tooManyLines = lines.length > maxLines;
        boolean lineTooLong = false;
        for (String line : lines) {
            if (line.length() > maxCharsPerLine) { lineTooLong = true; break; }
        }
        if (tooManyLines || lineTooLong) {
            bracketNameField.setText(oldText);
            bracketNameField.positionCaret(oldText.length());
        }
    });


        Label typeLabel = new Label("Bracket Type:");
        typeLabel.setTextFill(Color.web("#FFFFFF"));
        typeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        bracketTypeCombo = new ComboBox<>();
        bracketTypeCombo.getItems().addAll(
            "Single Elimination",
            "Double Elimination",
            "Round Robin",
            "Swiss System",
            "Free For All"
        );
        bracketTypeCombo.setValue("Single Elimination");
        bracketTypeCombo.setStyle("-fx-background-color: #152055; -fx-border-color: #7F8EE3; -fx-border-radius: 3;");

        bracketTypeCombo.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setStyle(empty || item == null ? null : "-fx-text-fill: #E0E6ED; -fx-background-color: #152055;");
            }
        });
        bracketTypeCombo.setButtonCell(new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setStyle(empty || item == null ? null : "-fx-text-fill: #E0E6ED;");
            }
        });

        bracketTypeCombo.setOnAction(e -> {
            rebuildTournament();
            updateBracketView();
            updateProgress();
        });

        Label sportLabel = new Label("Sport/Game:");
        sportLabel.setTextFill(Color.web("#FFFFFF"));
        sportLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        sportField = new TextField("");
        sportField.setPromptText("e.g. Basketball, Valorant...");
        sportField.setStyle("-fx-background-color: #152055; -fx-text-fill: #E0E6ED; -fx-border-color: #7F8EE3; -fx-border-radius: 3;");
        sportField.textProperty().addListener((obs, oldText, newText) -> {
        String[] lines = newText.split("\n", -1);
        int maxLines = 1;
        int maxCharsPerLine = 45;
        boolean tooManyLines = lines.length > maxLines;
        boolean lineTooLong = false;
        for (String line : lines) {
            if (line.length() > maxCharsPerLine) { lineTooLong = true; break; }
        }
        if (tooManyLines || lineTooLong) {
            sportField.setText(oldText);
            sportField.positionCaret(oldText.length());
        }
    });

       Label descLabel = new Label("Bracket Description:");
        descLabel.setTextFill(Color.web("#FFFFFF"));
        descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Enter tournament description...");
        descriptionArea.setPrefHeight(80);
        descriptionArea.setWrapText(true);
        descriptionArea.setStyle(
        "-fx-control-inner-background: #152055; -fx-background-color: transparent;" +
        "-fx-border-color: #7F8EE3; -fx-border-radius: 3;" +
        "-fx-prompt-text-fill: #e0e6edc2; -fx-text-fill: #FFFFFF;");
        descriptionArea.skinProperty().addListener((obs, oldSkin, newSkin) -> {
           
           
        ScrollPane sp = (ScrollPane) descriptionArea.lookup(".scroll-pane");
        if (sp != null) {
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
       sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }
        });
        descriptionArea.textProperty().addListener((obs, oldText, newText) -> {
        String[] lines = newText.split("\n", -1);
        int maxLines = 4;
        int maxCharsPerLine = 28;
        boolean tooManyLines = lines.length > maxLines;
        boolean lineTooLong = false;
        for (String line : lines) {
            if (line.length() > maxCharsPerLine) { lineTooLong = true; break; }
        }
        if (tooManyLines || lineTooLong) {
            descriptionArea.setText(oldText);
            descriptionArea.positionCaret(oldText.length());
        }
    });
        descriptionArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                int caret = descriptionArea.getCaretPosition();
                String current = descriptionArea.getText();
                if (current.length() < 220) {
                    descriptionArea.setText(current.substring(0, caret) + "\n" + current.substring(caret));
                    descriptionArea.positionCaret(caret + 1);
                }
                e.consume();
            }
        });

        Label statusTitle = new Label("BRACKET STATUS:");
        statusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #FFD862;");
        statusLabel = new Label("PENDING");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e74c3c;");

        Separator separator = new Separator();

        String blueN = "-fx-background-color: #7F8EE3; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String blueH = "-fx-background-color: #5a6abf; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String redN  = "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";
        String redH  = "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 8 16; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-color: transparent; -fx-font-weight: bold;";

        Button loadBtn       = new Button("LOAD BRACKET");
        Button saveBracketBtn = new Button("SAVE BRACKET");
        Button exitBtn       = new Button("EXIT");

        for (Button b : new Button[]{loadBtn, saveBracketBtn}) {
            b.setPrefWidth(Double.MAX_VALUE);
            b.setStyle(blueN);
            b.setOnMouseEntered(e -> b.setStyle(blueH));
            b.setOnMouseExited(e  -> b.setStyle(blueN));
        }
        exitBtn.setPrefWidth(Double.MAX_VALUE);
        exitBtn.setStyle(redN);
        exitBtn.setOnMouseEntered(e -> exitBtn.setStyle(redH));
        exitBtn.setOnMouseExited(e  -> exitBtn.setStyle(redN));

        loadBtn.setOnAction(e       -> loadBracket());
        saveBracketBtn.setOnAction(e -> saveBracket());
        exitBtn.setOnAction(e       -> System.exit(0));

        Button scoreMatrixBtn = new Button("SCORE MATRIX");
        scoreMatrixBtn.setPrefWidth(Double.MAX_VALUE);
        scoreMatrixBtn.setStyle(blueN);
        scoreMatrixBtn.setOnMouseEntered(e -> scoreMatrixBtn.setStyle(blueH));
        scoreMatrixBtn.setOnMouseExited(e  -> scoreMatrixBtn.setStyle(blueN));
        scoreMatrixBtn.setOnAction(e -> showScoreMatrix());

        Label tip = new Label("Quick Tip: You can save and load your brackets.");
        tip.setFont(Font.font("Arial", 10));
        tip.setTextFill(Color.web("#E0E6ED"));
        tip.setWrapText(true);

        panel.getChildren().addAll(
            title, nameLabel, bracketNameField,
            typeLabel, bracketTypeCombo,
            sportLabel, sportField,
            descLabel, descriptionArea,
            statusTitle, statusLabel,
            separator, loadBtn, saveBracketBtn, scoreMatrixBtn, exitBtn, tip
        );
        return panel;
    }

    // =========================================================================
    // BOTTOM PROGRESS BAR
    // =========================================================================
        private HBox createBottomProgressPanel() {
            HBox panel = new HBox(10);
            panel.setPadding(new Insets(10, 15, 10, 15));
            panel.setStyle("-fx-background-color: linear-gradient(to right, #040D43 0%, #040D43 50%, #FFBA09 100%); -fx-border-width: 1 0 0 0;");
            panel.setAlignment(Pos.CENTER_LEFT);

            Label progressTitle = new Label("Progress:");
            progressTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            progressTitle.setTextFill(Color.web("#FFD862"));

            progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(300);
            progressBar.setStyle("-fx-accent: #FFD862;");
            progressBar.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            progressBar.lookup(".bar").setStyle(
            "-fx-background-color: #FFBA09; -fx-background-radius: 3;");
            progressBar.lookup(".track").setStyle(
            "-fx-background-color: #152055; -fx-background-radius: 3; " +
            "-fx-border-color: #7F8EE3; -fx-border-radius: 3;");
 });

            progressLabel = new Label("0% Complete");
            progressLabel.setTextFill(Color.web("#FFD862"));
            progressLabel.setFont(Font.font("Arial", 11));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            panel.getChildren().addAll(progressTitle, progressBar, progressLabel, spacer);
            return panel;
        }

    // =========================================================================
    // BRACKET VIEW ROUTER
    // =========================================================================

    private String getNotReadyMessage(int n, String type) {
        if (n == 0) return "Single Elimination (4, 8, 12, 16, 24, 32) \nDouble Elimination (4, 8, 12, 16, 24, 32) \nRound Robin, Swiss System, and Free For All require at least 4 teams.";
        if ("Single Elimination".equals(type)) {
            if (n < 4) return "Need at least 4 Participants for Single Elimination.";
            int[] valid = {4, 8, 12, 16, 24, 32};
            for (int v : valid) {
                if (n == v) return "";
                if (v > n)  return "Add " + (v - n) + " more team(s) to reach " + v + " teams.\n(Valid: 4, 8, 12, 16, 24, 32)";
            }
            return "Single Elimination supports 4, 8, 12, 16, 24, or 32 teams.\nCurrent: " + n;
        }
        if ("Double Elimination".equals(type)) {
            if (n < 4)  return "Need at least 4 teams for Double Elimination.";
            if (isPowerOfTwo(n) || n == 24 || n == 12) return "";
            int[] valid = {4, 8, 12, 16, 24, 32};
            for (int v : valid) if (v > n) return "Add " + (v - n) + " more team(s) for " + v + "-team Double Elimination.\n(Valid: 4, 8, 12, 16, 24, 32)";
            return "Double Elimination supports 4, 8, 12, 16, 24, or 32 teams.\nCurrent: " + n;
        }
        // Round Robin, Swiss: 4+; Free For All: 4+
        if ("Free For All".equals(type)) {
            if (n < 4) return "Need at least 4 teams for Free For All.";
            return "";
        }
        if (n < 4) return "Need at least 4 teams for " + type + ".";
        return "";
    }

    private void updateBracketView() {
    bracketView.getChildren().clear();
    bracketView.setStyle(
        "-fx-background-color: #040D43;" +
        "-fx-border-color: #7F8EE3;" +
        "-fx-border-width: 1;" +
        "-fx-border-radius: 5;" +
        "-fx-background-radius: 5;" +
        "-fx-focus-color: transparent;" +
        "-fx-faint-focus-color: transparent;"
    );

    String currentType = bracketTypeCombo.getValue();  // ← must be declared BEFORE use

    if (tournament == null || !isValidTeamCount(teams.length, currentType)) {
        String fullMsg = getNotReadyMessage(teams.length, currentType);
        if (teams.length == 0 || fullMsg.startsWith("Supported")) {
            Label title = new Label("SUPPORTED FORMATS:");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            title.setTextFill(Color.web("#FFD862"));
            title.setPadding(new Insets(10, 10, 0, 10));
            Label msg = new Label(fullMsg.replace("Supported Formats:\n \n", ""));
            msg.setFont(Font.font("Arial", 13));
            msg.setTextFill(Color.web("#E0E6ED"));
            msg.setWrapText(true);
            msg.setPadding(new Insets(5, 10, 10, 10));
            bracketView.getChildren().addAll(title, msg);
        } else {
            Label msg = new Label(fullMsg);
            msg.setFont(Font.font("Arial", 13));
            msg.setTextFill(Color.web("#E0E6ED"));
            msg.setWrapText(true);
            msg.setPadding(new Insets(10));
            bracketView.getChildren().add(msg);
        }
        return;
    }

    switch (currentType) {
        case "Single Elimination":
            if (teams.length == 12) displaySingleElimination12();
            else displaySingleElimination();
            break;
        case "Double Elimination":
            if (teams.length == 12) displayDoubleElimination12();
            else if (teams.length == 6 || teams.length == 10
                    || teams.length == 20) displayPlayInDE();
            else displayDoubleElimination();
            break;
        case "Round Robin":  displayRoundRobin();  break;
        case "Swiss System": displaySwissSystem();  break;
        case "Free For All": displayFreeForAll();   break;
        default:             displaySingleElimination();
    }
}

    // =========================================================================
    // SINGLE ELIMINATION
    // =========================================================================

    private void displaySingleElimination() {
        final double CARD_W      = 180;
        final double CARD_H      = 68;
        final double CARD_MID    = CARD_H / 2.0;
        final double COL_GAP     = 70;
        final double MATCH_V_GAP = 24;
        final double TOP_PAD     = 70;

        int n           = teams.length;
        boolean hasByes = !isPowerOfTwo(n);

        int totalRounds = tournament.getTotalRounds();
        Map<Integer, List<Match>> byRound = new HashMap<>();
        for (int r = 1; r <= totalRounds; r++) {
            List<Match> ms = tournament.getMatchesByRound(r);
            if (!ms.isEmpty()) byRound.put(r, ms);
        }

        Map<Integer, List<Match>> visibleByRound = new HashMap<>();
        if (hasByes) {
            List<Match> r1All  = byRound.getOrDefault(1, new ArrayList<>());
            List<Match> r1Real = new ArrayList<>();
            for (Match m : r1All) {
                boolean isBye = m.isCompleted() && (m.getTeam1() == null || m.getTeam2() == null);
                if (!isBye) r1Real.add(m);
            }
            if (!r1Real.isEmpty()) visibleByRound.put(1, r1Real);
            for (int r = 2; r <= totalRounds; r++) {
                List<Match> ms = byRound.get(r);
                if (ms != null && !ms.isEmpty()) visibleByRound.put(r, ms);
            }
        } else {
            visibleByRound.putAll(byRound);
        }

        List<Integer> visRoundKeys = new ArrayList<>(visibleByRound.keySet());
        java.util.Collections.sort(visRoundKeys);
        int visRounds = visRoundKeys.size();

        Map<Match, Double> matchY = new HashMap<>();
        double slotH = CARD_H + MATCH_V_GAP;

        // Anchor col-0: evenly spaced
        if (!visRoundKeys.isEmpty()) {
            List<Match> anchor = visibleByRound.get(visRoundKeys.get(0));
            if (anchor != null)
                for (int i = 0; i < anchor.size(); i++)
                    matchY.put(anchor.get(i), TOP_PAD + i * slotH);
        }

        // Later rounds: midpoint of left/right child Y positions
        for (int ri = 1; ri < visRoundKeys.size(); ri++) {
            int r = visRoundKeys.get(ri);
            List<Match> curr = visibleByRound.get(r);
            if (curr == null) continue;
            for (Match m : curr) {
                Match lc = m.getLeftChild(), rc = m.getRightChild();
                Double ly = lc != null ? matchY.get(lc) : null;
                Double ry = rc != null ? matchY.get(rc) : null;
                if (ly == null && lc != null && !matchY.containsKey(lc)) ly = ry;
                if (ry == null && rc != null && !matchY.containsKey(rc)) ry = ly;
                if      (ly != null && ry != null) matchY.put(m, (ly + ry) / 2.0);
                else if (ly != null)               matchY.put(m, ly);
                else if (ry != null)               matchY.put(m, ry);
            }
        }

        double colStride = CARD_W + COL_GAP;
        double[] colX    = new double[visRounds];
        for (int i = 0; i < visRounds; i++) colX[i] = 20 + i * colStride;

        double maxY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double canvasW = visRounds > 0 ? colX[visRounds - 1] + CARD_W + 40 : 400;
        double canvasH = maxY + CARD_H + 60;

        Pane pane = new Pane();
        pane.setPrefSize(canvasW, canvasH);
        pane.setStyle("-fx-background-color: #040D43;");

        Canvas canvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#FFBA09"));
        gc.setLineWidth(2);

        for (int ri = 1; ri < visRoundKeys.size(); ri++) {
            int r = visRoundKeys.get(ri);
            List<Match> curr = visibleByRound.get(r);
            if (curr == null) continue;
            double srcRX = colX[ri - 1] + CARD_W, dstLX = colX[ri], midX = (srcRX + dstLX) / 2.0;
            for (Match m : curr) {
                if (!matchY.containsKey(m)) continue;
                double tgtY = matchY.get(m) + CARD_MID;
                Match lc = m.getLeftChild(), rc = m.getRightChild();
                if (lc != null && matchY.containsKey(lc))
                    drawBracketLine(gc, lc, matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
                if (rc != null && matchY.containsKey(rc))
                    drawBracketLine(gc, rc, matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
            }
        }
        pane.getChildren().add(canvas);

        for (int ri = 0; ri < visRoundKeys.size(); ri++) {
            int fromEnd = visRounds - 1 - ri;
            String rName;
            if (fromEnd == 0)      rName = "Championship";
            else if (fromEnd == 1) rName = "Finals";
            else if (fromEnd == 2) rName = "Semifinals";
            else if (fromEnd == 3) rName = "Quarterfinals";
            else                   rName = "Round " + (ri + 1);


            List<Match> rMs = visibleByRound.get(visRoundKeys.get(ri));
            if (rMs == null) continue;
            double topY = rMs.stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(Double.MAX_VALUE);
            if (topY == Double.MAX_VALUE) continue;
            Label lbl = styledLabel(rName, "#FFD862", FontWeight.BOLD, 11);
            lbl.setLayoutX(colX[ri]); lbl.setLayoutY(topY - 18);
            pane.getChildren().add(lbl);
        }

        for (int ri = 0; ri < visRoundKeys.size(); ri++) {
            boolean isLast = (ri == visRounds - 1);
            List<Match> ms = visibleByRound.get(visRoundKeys.get(ri));
            if (ms == null) continue;
            for (Match m : ms) {
                if (!matchY.containsKey(m)) continue;
                VBox card = createDeMatchCard(m, true, isLast);
                card.setLayoutX(colX[ri]); card.setLayoutY(matchY.get(m));
                card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                pane.getChildren().add(card);
            }
        }

        addScrollPane(pane, 550);
        Team champion = tournament.getTournamentWinner();
        if (champion != null) addChampionDisplay(champion);
    }

    // =========================================================================
    // SINGLE ELIMINATION – 12 teams (16-slot grid, top 4 seeds get byes)
    // Same visual engine as the generic SE renderer; the bracket data already
    // reflects the 12-team structure, so we just call displaySingleElimination().
    // =========================================================================

    private void displaySingleElimination12() {
        displaySingleElimination();
    }

    // =========================================================================
    // DOUBLE ELIMINATION – 12 teams
    // WB: 4 rounds (R1 opening, R2 vs bye seeds, R3 SF, R4 Finals)
    // LB: 6 rounds
    // Grand Final + optional Bracket Reset
    // =========================================================================

    private void displayDoubleElimination12() {
        final double CARD_W      = 180;
        final double CARD_H      = 68;
        final double CARD_MID    = CARD_H / 2.0;
        final double COL_GAP     = 70;
        final double MATCH_V_GAP = -15;
        final double LB_V_GAP    = 10;
        final double W_TOP_PAD   = 50;
        final double SECTION_GAP = 60;

        int winnersRounds = 4; // WB R1-R4

        // Collect WB matches by round (skip R1 bye matches for display)
        Map<Integer, List<Match>> winnersByRound = new HashMap<>();
        for (int r = 1; r <= winnersRounds; r++) {
            List<Match> ms = tournament.getWinnersMatchesByRound(r);
            if (!ms.isEmpty()) winnersByRound.put(r, ms);
        }

        // Build visible WB: R1 shows only real (non-bye) matches; R2-R4 show all
        Map<Integer, List<Match>> visWbByRound = new LinkedHashMap<>();
        for (int r = 1; r <= winnersRounds; r++) {
            List<Match> all = winnersByRound.get(r);
            if (all == null) continue;
            if (r == 1) {
                List<Match> real = new ArrayList<>();
                for (Match m : all) {
                    boolean isBye = m.isCompleted() && (m.getTeam1() == null || m.getTeam2() == null);
                    if (!isBye) real.add(m);
                }
                if (!real.isEmpty()) visWbByRound.put(r, real);
            } else {
                visWbByRound.put(r, all);
            }
        }
        List<Integer> wRoundKeys = new ArrayList<>(visWbByRound.keySet());
        Collections.sort(wRoundKeys);
        int wCols = wRoundKeys.size();

        // LB matches by round
        List<Match> losersAll = tournament.getLosersBracketMatches();
        Map<Integer, List<Match>> losersByRound = new LinkedHashMap<>();
        for (Match m : losersAll)
            losersByRound.computeIfAbsent(m.getRound(), k -> new ArrayList<>()).add(m);
        List<Integer> lRounds = new ArrayList<>(losersByRound.keySet());
        Collections.sort(lRounds);
        int lCols = lRounds.size();

        Match grandFinal = tournament.getGrandFinals();

        double colStride = CARD_W + COL_GAP;
        double[] wColX = new double[Math.max(wCols, 1)];
        for (int c = 0; c < wCols; c++) wColX[c] = 20 + c * colStride;
        double[] lColX = new double[Math.max(lCols, 1)];
        for (int c = 0; c < lCols; c++) lColX[c] = 20 + c * colStride;

        // Y positions for WB — use same algorithm as DE-24
        Map<Match, Double> matchY = new HashMap<>();
        double slotH = CARD_H + MATCH_V_GAP;

        // Anchor R1 (real matches)
        List<Match> r1Anchor = winnersByRound.getOrDefault(1, new ArrayList<>());
        for (int i = 0; i < r1Anchor.size(); i++)
            matchY.put(r1Anchor.get(i), W_TOP_PAD + i * slotH);

        for (int ri = 1; ri < wRoundKeys.size(); ri++) {
            List<Match> prev = visWbByRound.get(wRoundKeys.get(ri - 1));
            List<Match> curr = visWbByRound.get(wRoundKeys.get(ri));
            if (prev == null || curr == null) continue;
            for (int i = 0; i < curr.size(); i++) {
                if (curr.size() == prev.size()) {
                    if (matchY.containsKey(prev.get(i))) matchY.put(curr.get(i), matchY.get(prev.get(i)));
                } else {
                    int p1 = i * 2, p2 = i * 2 + 1;
                    Double y1 = (p1 < prev.size() && matchY.containsKey(prev.get(p1))) ? matchY.get(prev.get(p1)) : null;
                    Double y2 = (p2 < prev.size() && matchY.containsKey(prev.get(p2))) ? matchY.get(prev.get(p2)) : null;
                    if      (y1 != null && y2 != null) matchY.put(curr.get(i), (y1 + y2) / 2.0);
                    else if (y1 != null)               matchY.put(curr.get(i), y1);
                    else if (y2 != null)               matchY.put(curr.get(i), y2);
                }
            }
        }

        double winnersMaxY = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(W_TOP_PAD);
        double lbOffsetY   = winnersMaxY + CARD_H + SECTION_GAP;

        // LB Y positions
        if (!lRounds.isEmpty()) {
            List<Match> lbC0 = losersByRound.get(lRounds.get(0));
            double lbSlotH = CARD_H + LB_V_GAP;
            if (lbC0 != null)
                for (int i = 0; i < lbC0.size(); i++)
                    matchY.put(lbC0.get(i), lbOffsetY + 60 + i * lbSlotH);

            for (int ci = 1; ci < lRounds.size(); ci++) {
                List<Match> prev = losersByRound.get(lRounds.get(ci - 1));
                List<Match> curr = losersByRound.get(lRounds.get(ci));
                if (prev == null || curr == null) continue;
                if (curr.size() == prev.size()) {
                    for (int i = 0; i < curr.size(); i++)
                        if (matchY.containsKey(prev.get(i)))
                            matchY.put(curr.get(i), matchY.get(prev.get(i)));
                } else {
                    for (int i = 0; i < curr.size(); i++) {
                        int c1 = i * 2, c2 = i * 2 + 1;
                        double y1 = c1 < prev.size() && matchY.containsKey(prev.get(c1)) ? matchY.get(prev.get(c1)) : lbOffsetY + 60;
                        double y2 = c2 < prev.size() && matchY.containsKey(prev.get(c2)) ? matchY.get(prev.get(c2)) : y1;
                        matchY.put(curr.get(i), (y1 + y2) / 2.0);
                    }
                }
            }
        }

        // Grand Final Y: vertically centered
        double lbBotY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(lbOffsetY) + CARD_H;
        double gfCenterY = (W_TOP_PAD + lbBotY) / 2.0;
        double lastWbRX  = wCols > 0 ? wColX[wCols - 1] + CARD_W : 0;
        double lastLbRX  = lCols > 0 ? lColX[lCols - 1] + CARD_W : 0;
        double gfX = Math.max(lastWbRX, lastLbRX) + COL_GAP;
        double gfY = gfCenterY - CARD_MID;
        if (grandFinal != null) matchY.put(grandFinal, gfY);

        double maxY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(400);
        double canvasW = gfX + CARD_W + 60;
        double canvasH = maxY + CARD_H + 80;

        Pane pane = new Pane();
        pane.setPrefSize(canvasW, canvasH);
        pane.setStyle("-fx-background-color: #040D43;");

        Canvas canvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#FFBA09"));
        gc.setLineWidth(2);

        // WB connector lines
        for (int ri = 1; ri < wRoundKeys.size(); ri++) {
            List<Match> prev = visWbByRound.get(wRoundKeys.get(ri - 1));
            List<Match> curr = visWbByRound.get(wRoundKeys.get(ri));
            if (prev == null || curr == null) continue;
            double srcRX = wColX[ri - 1] + CARD_W;
            double dstLX = wColX[ri];
            double midX  = (srcRX + dstLX) / 2.0;
            for (int i = 0; i < curr.size(); i++) {
                Match m = curr.get(i);
                if (!matchY.containsKey(m)) continue;
                double tgtY = matchY.get(m) + CARD_MID;
                if (curr.size() == prev.size()) {
                    if (matchY.containsKey(prev.get(i))) {
                        double y = matchY.get(prev.get(i)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                } else {
                    int p1 = i * 2, p2 = i * 2 + 1;
                    if (p1 < prev.size() && matchY.containsKey(prev.get(p1))) {
                        double y = matchY.get(prev.get(p1)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                    if (p2 < prev.size() && matchY.containsKey(prev.get(p2))) {
                        double y = matchY.get(prev.get(p2)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                }
            }
        }

        // LB connector lines
        for (int ci = 1; ci < lRounds.size(); ci++) {
            List<Match> prev = losersByRound.get(lRounds.get(ci - 1));
            List<Match> curr = losersByRound.get(lRounds.get(ci));
            if (prev == null || curr == null) continue;
            double srcRX = lColX[ci - 1] + CARD_W;
            double dstLX = lColX[ci];
            double midX  = (srcRX + dstLX) / 2.0;
            int prevSize = prev.size(), currSize = curr.size();
            for (int i = 0; i < currSize; i++) {
                Match m = curr.get(i);
                if (!matchY.containsKey(m)) continue;
                double tgtY = matchY.get(m) + CARD_MID;
                if (currSize == prevSize) {
                    if (matchY.containsKey(prev.get(i))) {
                        double y = matchY.get(prev.get(i)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                } else {
                    int c1 = i * 2, c2 = i * 2 + 1;
                    if (c1 < prevSize && matchY.containsKey(prev.get(c1))) {
                        double y = matchY.get(prev.get(c1)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                    if (c2 < prevSize && matchY.containsKey(prev.get(c2))) {
                        double y = matchY.get(prev.get(c2)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                }
            }
        }

        // Grand Final feed lines
        if (grandFinal != null && matchY.containsKey(grandFinal)) {
            gc.setStroke(Color.web("#FFBA09")); gc.setLineWidth(2.5);
            double gfCY   = matchY.get(grandFinal) + CARD_MID;
            double mergeX = gfX - COL_GAP / 2.0;
            List<Match> lastW = visWbByRound.get(wRoundKeys.isEmpty() ? -1 : wRoundKeys.get(wRoundKeys.size() - 1));
            if (lastW != null && !lastW.isEmpty() && matchY.containsKey(lastW.get(0))) {
                double wy = matchY.get(lastW.get(0)) + CARD_MID;
                double wx = wColX[wCols - 1] + CARD_W;
                gc.strokeLine(wx, wy, mergeX, wy);
                gc.strokeLine(mergeX, wy, mergeX, gfCY);
                gc.strokeLine(mergeX, gfCY, gfX, gfCY);
            }
            if (!lRounds.isEmpty()) {
                List<Match> lastL = losersByRound.get(lRounds.get(lRounds.size() - 1));
                if (lastL != null && !lastL.isEmpty() && matchY.containsKey(lastL.get(0))) {
                    double ly = matchY.get(lastL.get(0)) + CARD_MID;
                    double lx = lColX[lCols - 1] + CARD_W;
                    gc.strokeLine(lx, ly, mergeX, ly);
                    gc.strokeLine(mergeX, ly, mergeX, gfCY);
                    gc.strokeLine(mergeX, gfCY, gfX, gfCY);
                }
            }
        }

        pane.getChildren().add(canvas);

        // Section labels
        double wbTopY = visWbByRound.isEmpty() ? W_TOP_PAD :
    visWbByRound.get(wRoundKeys.get(0)).stream()
        .filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(W_TOP_PAD);
double lbTopY = losersByRound.isEmpty() ? lbOffsetY + 60 :
    losersByRound.get(lRounds.get(0)).stream()
        .filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(lbOffsetY + 60);

addPaneLabel(pane, "WINNERS BRACKET", wCols > 0 ? wColX[0] : 20, wbTopY - 52, "#FFBA09", FontWeight.BOLD, 13);
addPaneLabel(pane, "LOSERS BRACKET",  lCols > 0 ? lColX[0] : 20, lbTopY - 52, "#FFBA09", FontWeight.BOLD, 13);
        // WB round labels
        for (int ri = 0; ri < wRoundKeys.size(); ri++) {
            int r = wRoundKeys.get(ri);
            List<Match> rMs = visWbByRound.get(r);
            if (rMs == null) continue;
            String rName = getWinnersRoundName(r, winnersRounds);
            Label rl = styledLabel(rName, "#FFD862", FontWeight.BOLD, 11);
            rl.setLayoutX(wColX[ri]);
            double topY = rMs.stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(W_TOP_PAD);
            rl.setLayoutY(topY - 18);
            pane.getChildren().add(rl);
        }

        // LB round labels
        for (int ci = 0; ci < lRounds.size(); ci++) {
            int r = lRounds.get(ci);
            List<Match> rms = losersByRound.get(r);
            double topY = rms.stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(lbOffsetY + 60);
            String lbLabel = (ci == lRounds.size() - 1) ? "Losers Final" : "Losers Round " + (ci + 1);
            Label rl = styledLabel(lbLabel, "#FFD862", FontWeight.BOLD, 11);
            rl.setLayoutX(lColX[ci]); rl.setLayoutY(topY - 18);
            pane.getChildren().add(rl);
        }

        // WB match cards
        for (int ri = 0; ri < wRoundKeys.size(); ri++) {
            List<Match> ms = visWbByRound.get(wRoundKeys.get(ri));
            if (ms == null) continue;
            for (Match m : ms) {
                if (!matchY.containsKey(m)) continue;
                VBox card = createDeMatchCard(m, true, false);
                card.setLayoutX(wColX[ri]); card.setLayoutY(matchY.get(m));
                card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                pane.getChildren().add(card);
            }
        }

        // LB match cards
        for (int ci = 0; ci < lRounds.size(); ci++) {
            int r = lRounds.get(ci);
            for (Match m : losersByRound.get(r)) {
                if (!matchY.containsKey(m)) continue;
                VBox card = createDeMatchCard(m, false, false);
                card.setLayoutX(lColX[ci]); card.setLayoutY(matchY.get(m));
                card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                pane.getChildren().add(card);
            }
        }

        // Grand Final card
        if (grandFinal != null && matchY.containsKey(grandFinal)) {
            VBox gfCard = createDeMatchCard(grandFinal, false, true);
            gfCard.setLayoutX(gfX); gfCard.setLayoutY(matchY.get(grandFinal));
            gfCard.setPrefWidth(CARD_W + 20); gfCard.setMaxWidth(CARD_W + 20);
            pane.getChildren().add(gfCard);
        }

        addScrollPane(pane, 600);
        Team champion = tournament.getTournamentWinner();
        if (champion != null) addChampionDisplay(champion);
    }

    // =========================================================================
    // DOUBLE ELIMINATION (standard: 4, 8, 16, 32)

    private void displayDoubleElimination() {
        int n         = teams.length;
        
        final double CARD_W      = 180;
        final double CARD_H      = 68;
        final double CARD_MID    = CARD_H / 2.0;
        final double COL_GAP     = 70;
        final double MATCH_V_GAP = (n == 32) ? 0 : (n == 24) ? -20 : (n == 16) ? 5 : 24;
        final double LB_V_GAP    = 10;
        final double W_TOP_PAD   = 70;
        final double SECTION_GAP = 60;

        int numRounds = (n == 24) ? 5 : (int)(Math.log(n) / Math.log(2));
        int winnersRounds = numRounds;
        boolean is24 = (n == 24);

        // ── Collect ALL WB matches by round (includes bye matches for 24-team) ─
        Map<Integer, List<Match>> winnersByRound = new HashMap<>();
        for (int r = 1; r <= winnersRounds; r++) {
            List<Match> ms = tournament.getWinnersMatchesByRound(r);
            if (!ms.isEmpty()) winnersByRound.put(r, ms);
        }

        // ── Build VISIBLE WB round map (skip bye-only matches from R1 for 24) ─
        // For 24-team: R1 has 16 slots (8 real + 8 byes). We only SHOW real ones.
        // Bye matches keep their Y so child-link midpoint works for R2+, but
        // they are excluded from the visible column used for card placement & lines.
        Map<Integer, List<Match>> visWbByRound = new LinkedHashMap<>();
        for (int r = 1; r <= winnersRounds; r++) {
            List<Match> all = winnersByRound.get(r);
            if (all == null) continue;
            if (is24 && r == 1) {
                // Only include real (non-bye) R1 matches in the visible map
                List<Match> real = new ArrayList<>();
                for (Match m : all) {
                    boolean isBye = m.isCompleted() && (m.getTeam1() == null || m.getTeam2() == null);
                    if (!isBye) real.add(m);
                }
                if (!real.isEmpty()) visWbByRound.put(r, real);
            } else {
                visWbByRound.put(r, all);
            }
        }
        List<Integer> wRoundKeys = new ArrayList<>(visWbByRound.keySet());
        Collections.sort(wRoundKeys);
        int wCols = wRoundKeys.size();

        // ── Collect LB matches by round ──────────────────────────────────────
        List<Match> losersAll = tournament.getLosersBracketMatches();
        Map<Integer, List<Match>> losersByRound = new LinkedHashMap<>();
        for (Match m : losersAll) {
            losersByRound.computeIfAbsent(m.getRound(), k -> new ArrayList<>()).add(m);
        }
        List<Integer> lRounds = new ArrayList<>(losersByRound.keySet());
        Collections.sort(lRounds);
        int lCols = lRounds.size();

        Match grandFinal = tournament.getGrandFinals();

        double colStride = CARD_W + COL_GAP;
        double[] wColX = new double[Math.max(wCols, 1)];
        for (int c = 0; c < wCols; c++) wColX[c] = 20 + c * colStride;
        double[] lColX = new double[Math.max(lCols, 1)];
        for (int c = 0; c < lCols; c++) lColX[c] = 20 + c * colStride;

        Map<Match, Double> matchY = new HashMap<>();
        double slotH = CARD_H + MATCH_V_GAP;

        List<Match> r1Anchor = (!wRoundKeys.isEmpty()) ? visWbByRound.get(wRoundKeys.get(0)) : new ArrayList<>();
        if (is24 && winnersByRound.containsKey(1)) r1Anchor = winnersByRound.get(1);
        if (r1Anchor != null)
            for (int i = 0; i < r1Anchor.size(); i++)
                matchY.put(r1Anchor.get(i), W_TOP_PAD + i * slotH);

        for (int ri = 1; ri < wRoundKeys.size(); ri++) {
            List<Match> prev = visWbByRound.get(wRoundKeys.get(ri - 1));
            List<Match> curr = visWbByRound.get(wRoundKeys.get(ri));
            if (prev == null || curr == null) continue;
            for (int i = 0; i < curr.size(); i++) {
                if (curr.size() == prev.size()) {
                    if (matchY.containsKey(prev.get(i))) matchY.put(curr.get(i), matchY.get(prev.get(i)));
                } else {
                    int p1 = i * 2, p2 = i * 2 + 1;
                    Double y1 = (p1 < prev.size() && matchY.containsKey(prev.get(p1))) ? matchY.get(prev.get(p1)) : null;
                    Double y2 = (p2 < prev.size() && matchY.containsKey(prev.get(p2))) ? matchY.get(prev.get(p2)) : null;
                    if      (y1 != null && y2 != null) matchY.put(curr.get(i), (y1 + y2) / 2.0);
                    else if (y1 != null)               matchY.put(curr.get(i), y1);
                    else if (y2 != null)               matchY.put(curr.get(i), y2);
                }
            }
        }

        double winnersMaxY = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(W_TOP_PAD);
        double lbOffsetY   = winnersMaxY + CARD_H + SECTION_GAP;

        // LB col-0: evenly spaced
        if (!lRounds.isEmpty()) {
            List<Match> lbC0 = losersByRound.get(lRounds.get(0));
            double lbSlotH = CARD_H + LB_V_GAP;
            if (lbC0 != null)
                for (int i = 0; i < lbC0.size(); i++)
                    matchY.put(lbC0.get(i), lbOffsetY + 60 + i * lbSlotH);

            // LB later columns: midpoint or 1:1 depending on count
            for (int ci = 1; ci < lRounds.size(); ci++) {
                List<Match> prev = losersByRound.get(lRounds.get(ci - 1));
                List<Match> curr = losersByRound.get(lRounds.get(ci));
                if (prev == null || curr == null) continue;
                int prevSize = prev.size(), currSize = curr.size();
                if (currSize == prevSize) {
                    for (int i = 0; i < currSize; i++)
                        if (matchY.containsKey(prev.get(i)))
                            matchY.put(curr.get(i), matchY.get(prev.get(i)));
                } else {
                    for (int i = 0; i < currSize; i++) {
                        int c1 = i * 2, c2 = i * 2 + 1;
                        double y1 = c1 < prevSize && matchY.containsKey(prev.get(c1)) ? matchY.get(prev.get(c1)) : lbOffsetY + 60;
                        double y2 = c2 < prevSize && matchY.containsKey(prev.get(c2)) ? matchY.get(prev.get(c2)) : y1;
                        matchY.put(curr.get(i), (y1 + y2) / 2.0);
                    }
                }
            }
        }

        // Grand Final: vertically centered between WB and LB
        double lbBotY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(lbOffsetY) + CARD_H;
        double gfCenterY = (W_TOP_PAD + lbBotY) / 2.0;
        double lastWbRX  = wCols > 0 ? wColX[wCols - 1] + CARD_W : 0;
        double lastLbRX  = lCols > 0 ? lColX[lCols - 1] + CARD_W : 0;
        double gfX = Math.max(lastWbRX, lastLbRX) + COL_GAP;
        double gfY = gfCenterY - CARD_MID;
        if (grandFinal != null) matchY.put(grandFinal, gfY);

        // ── Canvas sizing ────────────────────────────────────────────────────
        double maxY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(400);
        double canvasW = gfX + CARD_W + 60;
        double canvasH = maxY + CARD_H + 80;

        Pane pane = new Pane();
        pane.setPrefSize(canvasW, canvasH);
        pane.setStyle("-fx-background-color: #040D43;");

        Canvas canvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#FFBA09"));
        gc.setLineWidth(2);

        // WB connector lines: 1:1 rounds draw straight across; 2:1 rounds draw branching pair
        for (int ri = 1; ri < wRoundKeys.size(); ri++) {
            List<Match> prev = visWbByRound.get(wRoundKeys.get(ri - 1));
            List<Match> curr = visWbByRound.get(wRoundKeys.get(ri));
            if (prev == null || curr == null) continue;
            double srcRX = wColX[ri - 1] + CARD_W;
            double dstLX = wColX[ri];
            double midX  = (srcRX + dstLX) / 2.0;
            for (int i = 0; i < curr.size(); i++) {
                Match m = curr.get(i);
                if (!matchY.containsKey(m)) continue;
                double tgtY = matchY.get(m) + CARD_MID;
                if (curr.size() == prev.size()) {
                    // 1:1: straight line from prev[i] to curr[i]
                    if (matchY.containsKey(prev.get(i))) {
                        double y = matchY.get(prev.get(i)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                } else {
                    // 2:1: two feeders branch into one
                    int p1 = i * 2, p2 = i * 2 + 1;
                    if (p1 < prev.size() && matchY.containsKey(prev.get(p1))) {
                        double y = matchY.get(prev.get(p1)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                    if (p2 < prev.size() && matchY.containsKey(prev.get(p2))) {
                        double y = matchY.get(prev.get(p2)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                }
            }
        }


        // ── LB connector lines ───────────────────────────────────────────────
        for (int ci = 1; ci < lRounds.size(); ci++) {
            List<Match> prev = losersByRound.get(lRounds.get(ci - 1));
            List<Match> curr = losersByRound.get(lRounds.get(ci));
            if (prev == null || curr == null) continue;
            double srcRX = lColX[ci - 1] + CARD_W;
            double dstLX = lColX[ci];
            double midX  = (srcRX + dstLX) / 2.0;
            int prevSize = prev.size(), currSize = curr.size();
            for (int i = 0; i < currSize; i++) {
                Match m = curr.get(i);
                if (!matchY.containsKey(m)) continue;
                double tgtY = matchY.get(m) + CARD_MID;
                if (currSize == prevSize) {
                    if (matchY.containsKey(prev.get(i))) {
                        double y = matchY.get(prev.get(i)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                } else {
                    int c1 = i * 2, c2 = i * 2 + 1;
                    if (c1 < prevSize && matchY.containsKey(prev.get(c1))) {
                        double y = matchY.get(prev.get(c1)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                    if (c2 < prevSize && matchY.containsKey(prev.get(c2))) {
                        double y = matchY.get(prev.get(c2)) + CARD_MID;
                        gc.strokeLine(srcRX, y, midX, y);
                        gc.strokeLine(midX, y, midX, tgtY);
                        gc.strokeLine(midX, tgtY, dstLX, tgtY);
                    }
                }
            }
        }

        // ── Grand Final feed lines ───────────────────────────────────────────
        if (grandFinal != null && matchY.containsKey(grandFinal)) {
            gc.setStroke(Color.web("#FFBA09")); gc.setLineWidth(2.5);
            double gfCY   = matchY.get(grandFinal) + CARD_MID;
            double mergeX = gfX - COL_GAP / 2.0;
            // WB final → GF
            List<Match> lastW = visWbByRound.get(wRoundKeys.isEmpty() ? -1 : wRoundKeys.get(wRoundKeys.size() - 1));
            if (lastW != null && !lastW.isEmpty() && matchY.containsKey(lastW.get(0))) {
                double wy = matchY.get(lastW.get(0)) + CARD_MID;
                double wx = wColX[wCols - 1] + CARD_W;
                gc.strokeLine(wx, wy, mergeX, wy);
                gc.strokeLine(mergeX, wy, mergeX, gfCY);
                gc.strokeLine(mergeX, gfCY, gfX, gfCY);
            }
            // LB final → GF
            if (!lRounds.isEmpty()) {
                List<Match> lastL = losersByRound.get(lRounds.get(lRounds.size() - 1));
                if (lastL != null && !lastL.isEmpty() && matchY.containsKey(lastL.get(0))) {
                    double ly = matchY.get(lastL.get(0)) + CARD_MID;
                    double lx = lColX[lCols - 1] + CARD_W;
                    gc.strokeLine(lx, ly, mergeX, ly);
                    gc.strokeLine(mergeX, ly, mergeX, gfCY);
                    gc.strokeLine(mergeX, gfCY, gfX, gfCY);
                }
            }
        }

        pane.getChildren().add(canvas);

        // ── Section labels ───────────────────────────────────────────────────
        double wbTopY = matchY.entrySet().stream()
        .filter(e -> visWbByRound.values().stream().anyMatch(list -> list.contains(e.getKey())))
        .mapToDouble(Map.Entry::getValue).min().orElse(W_TOP_PAD);
        double lbTopY = matchY.entrySet().stream()
        .filter(e -> losersByRound.values().stream().anyMatch(list -> list.contains(e.getKey())))
        .mapToDouble(Map.Entry::getValue).min().orElse(lbOffsetY + 60);

        addPaneLabel(pane, "WINNERS BRACKET", wCols > 0 ? wColX[0] : 20, wbTopY - 52, "#FFBA09", FontWeight.BOLD, 13);
        addPaneLabel(pane, "LOSERS BRACKET",  lCols > 0 ? lColX[0] : 20, lbTopY - 52, "#FFBA09", FontWeight.BOLD, 13);

        // ── WB round labels ──────────────────────────────────────────────────
        for (int ri = 0; ri < wRoundKeys.size(); ri++) {
            int r = wRoundKeys.get(ri);
            List<Match> rMs = visWbByRound.get(r);
            if (rMs == null) continue;
            String rName = getWinnersRoundName(r, winnersRounds);
            Label rl = styledLabel(rName, "#FFD862", FontWeight.BOLD, 11);
            rl.setLayoutX(wColX[ri]);
            double topY = rMs.stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(W_TOP_PAD);
            rl.setLayoutY(topY - 18);
            pane.getChildren().add(rl);
        }

        // ── LB round labels ──────────────────────────────────────────────────
        for (int ci = 0; ci < lRounds.size(); ci++) {
            int r = lRounds.get(ci);
            List<Match> rms = losersByRound.get(r);
            double topY = rms.stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(lbOffsetY + 60);
            Label rl = styledLabel("Losers Round " + (ci + 1), "#FFD862", FontWeight.BOLD, 11);
            rl.setLayoutX(lColX[ci]); rl.setLayoutY(topY - 18);
            pane.getChildren().add(rl);
        }

        // ── WB match cards ───────────────────────────────────────────────────
        for (int ri = 0; ri < wRoundKeys.size(); ri++) {
            List<Match> ms = visWbByRound.get(wRoundKeys.get(ri));
            if (ms == null) continue;
            for (Match m : ms) {
                if (!matchY.containsKey(m)) continue;
                VBox card = createDeMatchCard(m, true, false);
                card.setLayoutX(wColX[ri]); card.setLayoutY(matchY.get(m));
                card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                pane.getChildren().add(card);
            }
        }

        // ── LB match cards ───────────────────────────────────────────────────
        for (int ci = 0; ci < lRounds.size(); ci++) {
            int r = lRounds.get(ci);
            for (Match m : losersByRound.get(r)) {
                if (!matchY.containsKey(m)) continue;
                VBox card = createDeMatchCard(m, false, false);
                card.setLayoutX(lColX[ci]); card.setLayoutY(matchY.get(m));
                card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                pane.getChildren().add(card);
            }
        }

        // ── Grand Final card ─────────────────────────────────────────────────
        if (grandFinal != null && matchY.containsKey(grandFinal)) {
            VBox gfCard = createDeMatchCard(grandFinal, false, true);
            gfCard.setLayoutX(gfX); gfCard.setLayoutY(matchY.get(grandFinal));
            gfCard.setPrefWidth(CARD_W + 20); gfCard.setMaxWidth(CARD_W + 20);
            pane.getChildren().add(gfCard);
        }

        addScrollPane(pane, (n == 32) ? 750 : 600);
        Team champion = tournament.getTournamentWinner();
        if (champion != null) addChampionDisplay(champion);
    }
    // =========================================================================
    // PLAY-IN DOUBLE ELIMINATION
    // Only renders play-in sections (WB / LB / GF) when they actually have matches.
    // =========================================================================

    private void displayPlayInDE() {
        final double CARD_W      = 180;
        final double CARD_H      = 68;
        final double CARD_MID    = CARD_H / 2.0;
        final double COL_GAP     = 70;
        final double MATCH_V_GAP = 25;
        final double TOP_PAD     = 30;
        final double SECTION_GAP = 120;

        // ── Gather play-in WB matches ─────────────────────────────────
        int piWbRounds = tournament.getPlayInWinnersRounds();
        Map<Integer, List<Match>> piWbByRound = new HashMap<>();
        for (int r = 1; r <= piWbRounds; r++) {
            List<Match> ms = tournament.getPlayInWinnersMatchesByRound(r);
            if (!ms.isEmpty()) piWbByRound.put(r, ms);
        }
        boolean hasPiWb = !piWbByRound.isEmpty();

        // ── Gather play-in LB matches ─────────────────────────────────
        Map<Integer, List<Match>> piLbByRound = new HashMap<>();
        for (Match m : tournament.getLosersBracketMatches())
            if (m.isPlayIn()) piLbByRound.computeIfAbsent(m.getRound(), k -> new ArrayList<>()).add(m);
        boolean hasPiLb = !piLbByRound.isEmpty();

        List<Integer> piLbRounds = new ArrayList<>(piLbByRound.keySet());
        java.util.Collections.sort(piLbRounds);
        Map<Integer, Integer> piLbRoundToCol = new HashMap<>();
        for (int i = 0; i < piLbRounds.size(); i++) piLbRoundToCol.put(piLbRounds.get(i), i);

        Match piGF = tournament.getPlayInGrandFinal();
        boolean hasPiGF = piGF != null;


        int totalPiCols = Math.max(hasPiWb ? piWbRounds : 0, hasPiLb ? piLbRounds.size() : 0);
        double colStride = CARD_W + COL_GAP;
        double[] colX = new double[Math.max(totalPiCols + 1, 1)];
        for (int c = 0; c <= totalPiCols; c++) colX[c] = 20 + c * colStride;
        double piGfX = totalPiCols > 0 ? colX[totalPiCols] : 20;

        // ── Y positions: play-in WB ───────────────────────────────────
        Map<Match, Double> matchY = new HashMap<>();
        if (hasPiWb && piWbByRound.containsKey(1)) {
            List<Match> r1 = piWbByRound.get(1);
            double slotH = CARD_H + MATCH_V_GAP;
            for (int i = 0; i < r1.size(); i++) matchY.put(r1.get(i), TOP_PAD + i * slotH);
        }
        for (int r = 2; r <= piWbRounds; r++) {
            List<Match> curr = piWbByRound.get(r);
            if (curr == null) continue;
            for (Match m : curr) {
                Double ly = m.getLeftChild()  != null ? matchY.get(m.getLeftChild())  : null;
                Double ry = m.getRightChild() != null ? matchY.get(m.getRightChild()) : null;
                if      (ly != null && ry != null) matchY.put(m, (ly + ry) / 2.0);
                else if (ly != null)               matchY.put(m, ly);
                else if (ry != null)               matchY.put(m, ry);
            }
        }

        // ── Y positions: play-in LB ───────────────────────────────────
        double wbMaxY = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double lbOffsetY = wbMaxY + (hasPiWb ? CARD_H + SECTION_GAP : TOP_PAD);
        if (hasPiLb && !piLbRounds.isEmpty()) {
            List<Match> lr1 = piLbByRound.get(piLbRounds.get(0));
            if (lr1 != null) {
                double slotH = CARD_H + MATCH_V_GAP;
                for (int i = 0; i < lr1.size(); i++) matchY.put(lr1.get(i), lbOffsetY + 60 + i * slotH);
            }
            for (int ri = 1; ri < piLbRounds.size(); ri++) {
                List<Match> curr = piLbByRound.get(piLbRounds.get(ri));
                if (curr == null) continue;
                for (Match m : curr) {
                    Double ly = m.getLeftChild()  != null ? matchY.get(m.getLeftChild())  : null;
                    Double ry = m.getRightChild() != null ? matchY.get(m.getRightChild()) : null;
                    if      (ly != null && ry != null) matchY.put(m, (ly + ry) / 2.0);
                    else if (ly != null)               matchY.put(m, ly);
                    else if (ry != null)               matchY.put(m, ry);
                }
            }
        }

        // ── Y position: play-in GF ────────────────────────────────────
        double piLbBotY = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(TOP_PAD) + CARD_H;
        double piGfY    = (TOP_PAD + piLbBotY) / 2.0 - CARD_MID;
        if (hasPiGF) matchY.put(piGF, piGfY);

        // ── Canvas ────────────────────────────────────────────────────
        double maxY    = matchY.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double canvasW = piGfX + CARD_W + 60;
        double canvasH = maxY + CARD_H + 60;

        Pane pane = new Pane();
        pane.setPrefSize(canvasW, canvasH);
        pane.setStyle("-fx-background-color: #040D43;");

        Canvas canvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setLineWidth(2);

        // ── Play-in WB lines (only when play-in WB exists) ───────────
        if (hasPiWb) {
            gc.setStroke(Color.web("#FFBA09"));
            for (int r = 2; r <= piWbRounds; r++) {
                List<Match> curr = piWbByRound.get(r);
                if (curr == null) continue;
                double srcRX = colX[r - 2] + CARD_W, dstLX = colX[r - 1], midX = (srcRX + dstLX) / 2.0;
                for (Match m : curr) {
                    if (!matchY.containsKey(m)) continue;
                    double tgtY = matchY.get(m) + CARD_MID;
                    drawBracketLine(gc, m.getLeftChild(),  matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
                    drawBracketLine(gc, m.getRightChild(), matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
                }
            }
        }

        // ── Play-in LB lines (only when play-in LB exists) ───────────
        if (hasPiLb) {
            gc.setStroke(Color.web("#FFBA09"));
            for (int ri = 1; ri < piLbRounds.size(); ri++) {
                List<Match> currMs = piLbByRound.get(piLbRounds.get(ri - 1));
                List<Match> nextMs = piLbByRound.get(piLbRounds.get(ri));
                if (currMs == null || nextMs == null) continue;
                double srcRX = colX[piLbRoundToCol.get(piLbRounds.get(ri - 1))] + CARD_W;
                double dstLX = colX[piLbRoundToCol.get(piLbRounds.get(ri))];
                double midX  = (srcRX + dstLX) / 2.0;
                for (Match m : nextMs) {
                    if (!matchY.containsKey(m)) continue;
                    double tgtY = matchY.get(m) + CARD_MID;
                    Match lc = m.getLeftChild(), rc = m.getRightChild();
                    if (lc != null && matchY.containsKey(lc) && currMs.contains(lc))
                        drawBracketLine(gc, lc, matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
                    if (rc != null && matchY.containsKey(rc) && currMs.contains(rc))
                        drawBracketLine(gc, rc, matchY, srcRX, midX, dstLX, tgtY, CARD_MID);
                }
            }
        }

        // ── Play-in GF feed lines (only when play-in GF exists) ──────
        if (hasPiGF && matchY.containsKey(piGF)) {
            gc.setStroke(Color.web("#f39c12")); gc.setLineWidth(2.5);
            double gfCY = matchY.get(piGF) + CARD_MID, mergeX = piGfX - COL_GAP / 2.0;
            if (hasPiWb && piWbByRound.containsKey(piWbRounds)) {
                List<Match> wbFinals = piWbByRound.get(piWbRounds);
                if (!wbFinals.isEmpty() && matchY.containsKey(wbFinals.get(0))) {
                    double wy = matchY.get(wbFinals.get(0)) + CARD_MID, wx = colX[piWbRounds - 1] + CARD_W;
                    gc.strokeLine(wx, wy, mergeX, wy); gc.strokeLine(mergeX, wy, mergeX, gfCY); gc.strokeLine(mergeX, gfCY, piGfX, gfCY);
                }
            }
            if (hasPiLb && !piLbRounds.isEmpty()) {
                List<Match> lbFinals = piLbByRound.get(piLbRounds.get(piLbRounds.size() - 1));
                if (lbFinals != null && !lbFinals.isEmpty() && matchY.containsKey(lbFinals.get(0))) {
                    double ly = matchY.get(lbFinals.get(0)) + CARD_MID;
                    double lx = colX[piLbRoundToCol.get(piLbRounds.get(piLbRounds.size() - 1))] + CARD_W;
                    gc.strokeLine(lx, ly, mergeX, ly); gc.strokeLine(mergeX, ly, mergeX, gfCY); gc.strokeLine(mergeX, gfCY, piGfX, gfCY);
                }
            }
        }

        pane.getChildren().add(canvas);

        // ── Section labels (only shown when the section has content) ─
        if (hasPiWb)
            addPaneLabel(pane, "WINNERS BRACKET", colX[0], TOP_PAD - 70, "#FFBA09", FontWeight.BOLD, 13);
        if (hasPiLb)
            addPaneLabel(pane, "LOSERS BRACKET",  colX[0], lbOffsetY + 10, "#FFBA09", FontWeight.BOLD, 13);
        if (hasPiGF)
            addPaneLabel(pane, "Grand Finals", piGfX, piGfY - 22, "#FFD862", FontWeight.BOLD, 12);
        // Play-in WB round labels
        if (hasPiWb) {
            for (Map.Entry<Integer, List<Match>> e : piWbByRound.entrySet()) {
                int r = e.getKey();
                double topY = e.getValue().stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(Double.MAX_VALUE);
                if (topY == Double.MAX_VALUE) continue;
                int fromEnd = piWbRounds - r;
                String rName = fromEnd == 0 ? "Upper Final" : fromEnd == 1 ? "Upper Semifinals" : "Upper Round " + r;
                Label rl = styledLabel(rName, "#FFD862", FontWeight.BOLD, 11);
                rl.setLayoutX(colX[r - 1]); rl.setLayoutY(topY - 18);
                pane.getChildren().add(rl);
            }
        }
        // Play-in LB round labels
        if (hasPiLb) {
            for (int r : piLbRounds) {
                int col = piLbRoundToCol.get(r);
                double topY = piLbByRound.get(r).stream().filter(matchY::containsKey).mapToDouble(matchY::get).min().orElse(Double.MAX_VALUE);
                if (topY == Double.MAX_VALUE) continue;
                Label rl = styledLabel("Losers Round " + (col + 1), "#FFD862", FontWeight.BOLD, 11);
                rl.setLayoutX(colX[col]); rl.setLayoutY(topY - 18);
                pane.getChildren().add(rl);
            }
        }   
        if (hasPiWb) {
            for (Map.Entry<Integer, List<Match>> e : piWbByRound.entrySet()) {
                int r = e.getKey();
                for (Match m : e.getValue()) {
                    if (!matchY.containsKey(m)) continue;
                    VBox card = createDeMatchCard(m, true, false);
                    card.setLayoutX(colX[r - 1]); card.setLayoutY(matchY.get(m));
                    card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                    pane.getChildren().add(card);
                }
            }
        }
        if (hasPiLb) {
            for (int r : piLbRounds) {
                int col = piLbRoundToCol.get(r);
                for (Match m : piLbByRound.get(r)) {
                    if (!matchY.containsKey(m)) continue;
                    VBox card = createDeMatchCard(m, false, false);
                    card.setLayoutX(colX[col]); card.setLayoutY(matchY.get(m));
                    card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
                    pane.getChildren().add(card);
                }
            }
        }
        // Play-in GF card
        if (hasPiGF && matchY.containsKey(piGF)) {
            VBox card = createDeMatchCard(piGF, false, true);
            card.setLayoutX(piGfX); card.setLayoutY(matchY.get(piGF));
            card.setPrefWidth(CARD_W); card.setMaxWidth(CARD_W);
            pane.getChildren().add(card);
        }
        addScrollPane(pane, 600);
        Team champion = tournament.getTournamentWinner();
        if (champion != null) addChampionDisplay(champion);
    }

 
   
    private VBox createDeMatchCard(Match match, boolean isWinners, boolean isGrandFinal) {
        String border = "#FFBA09";
        String t1Name = match.getTeam1() != null ? match.getTeam1().getName() : "TBD";
        String t2Name = match.getTeam2() != null ? match.getTeam2().getName() : "TBD";
        boolean done  = match.isCompleted();
        Team winner   = match.getWinner();
        String score  = match.getScore() != null ? match.getScore() : "";

        boolean canReport = !done && match.getTeam1() != null && match.getTeam2() != null
        && !t1Name.equals("TBD") && !t2Name.equals("TBD");

        String normalStyle  = "-fx-background-color:#152055;-fx-border-color:" + border + ";-fx-border-width:2;-fx-border-radius:4;-fx-background-radius:4;-fx-focus-color:transparent;-fx-faint-focus-color:transparent;" + (canReport ? "-fx-cursor:hand;" : "");
        String hoverStyle   = "-fx-background-color:#1a2a6a;-fx-border-color:#FFBA09;-fx-border-width:2;-fx-border-radius:4;-fx-background-radius:4;-fx-focus-color:transparent;-fx-faint-focus-color:transparent;-fx-cursor:hand;";
        String pressedStyle = "-fx-background-color:#152055;-fx-border-color:#FFBA09;-fx-border-width:2;-fx-border-radius:4;-fx-background-radius:4;-fx-focus-color:transparent;-fx-faint-focus-color:transparent;-fx-cursor:hand;";

        VBox card = new VBox(2);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(4, 8, 4, 8));
        card.setFocusTraversable(false);
        card.setStyle(normalStyle);
        if (canReport) {
            card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
            card.setOnMouseExited(e  -> card.setStyle(normalStyle));
            card.setOnMousePressed(e -> card.setStyle(pressedStyle));
            card.setOnMouseReleased(e -> card.setStyle(hoverStyle));
            card.setOnMouseClicked(e -> showScoreDialog(match));
        }

        card.getChildren().addAll(
            makeTeamRow(t1Name, score, 0, done, winner),
            makeSeparator(),
            makeTeamRow(t2Name, score, 1, done, winner)
        );

        return card;
    }

    private HBox makeTeamRow(String teamName, String score, int teamIndex, boolean done, Team winner) {
    HBox row = new HBox(4);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(1, 2, 1, 2));
    Label lbl = new Label(teamName);
    lbl.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    lbl.setMaxWidth(120);
    lbl.setWrapText(false);
    lbl.setStyle("-fx-text-fill: #E0E6ED;");
    Label sc = new Label();
    sc.setFont(Font.font("Arial", 11));
    sc.setStyle("-fx-text-fill: #FFD862;");
    if (done && !score.isEmpty()) {
        String[] p = score.split("-");
        if (teamIndex < p.length) sc.setText(p[teamIndex].trim());
        boolean isWinner = winner != null && winner.getName().equals(teamName);
        if (isWinner) {
            lbl.setStyle("-fx-text-fill: #FFC107 ; -fx-font-weight: bold;");
            sc.setStyle("-fx-text-fill: #FFC107 ;");
        } else {
            lbl.setStyle("-fx-text-fill: #FCEB92 ; -fx-font-weight: normal;");
            sc.setStyle("-fx-text-fill: #FCEB92 ;");
            row.setStyle("-fx-background-color: #eeeeee80; -fx-background-radius: 3;");
        }
    }
    Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    row.getChildren().addAll(lbl, spacer, sc);
    return row;
}
    private Separator makeSeparator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #FFBA09;");
        return sep;
    }

    private String getWinnersRoundName(int round, int totalWinnersRounds) {
    int fromEnd = totalWinnersRounds - round;
    if (fromEnd == 0) return "Upper Finals";
    if (fromEnd == 1) return "Upper Semifinals";
    if (fromEnd == 2) return "Quarterfinals";
    return "Upper Round " + round;
}

    // =========================================================================
    // ROUND ROBIN / SWISS / FREE FOR ALL
    // =========================================================================


    private void displayRoundRobin() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #040D43;");

        Label title = new Label("ROUND ROBIN – Standings & Results");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#FFD862"));
        container.getChildren().add(title);

        // ── Standings table ───────────────────────────────────────────────
        GridPane grid = makeStandingsGrid(new String[]{"Rank","Team","Wins","Losses","Win %","PD"}, "#040D43");
        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));
        sorted.sort((a, b) -> {
            int w = Integer.compare(b.getWins(), a.getWins());
            return w != 0 ? w : Integer.compare(b.getPointDifference(), a.getPointDifference());
        });
        for (int i = 0; i < sorted.size(); i++) {
            Team t = sorted.get(i);
            addStandingsRow(grid, i + 1, new String[]{
                String.valueOf(i + 1), t.getName(),
                String.valueOf(t.getWins()), String.valueOf(t.getLosses()),
                String.format("%.1f%%", t.getWinPercentage()),
                String.valueOf(t.getPointDifference())
            });
        }
        container.getChildren().add(grid);

        // ── Matches grouped by round ──────────────────────────────────────
        int totalRounds = tournament.getTotalRounds();
        for (int r = 1; r <= totalRounds; r++) {
        List<Match> roundMatches = tournament.getMatchesByRound(r);
        if (roundMatches.isEmpty()) continue;

        Label roundLbl = new Label("Round " + r);
        roundLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        roundLbl.setTextFill(Color.web("#FFBA09"));
        roundLbl.setPadding(new Insets(8, 0, 2, 0));
        container.getChildren().add(roundLbl);

        for (int i = 0; i < roundMatches.size(); i += 2) {
        HBox pair = new HBox(8);
        pair.setFillHeight(true);

        HBox left = createMatchResultRow(roundMatches.get(i));
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        pair.getChildren().add(left);

        if (i + 1 < roundMatches.size()) {
            HBox right = createMatchResultRow(roundMatches.get(i + 1));
            HBox.setHgrow(right, Priority.ALWAYS);
            right.setMaxWidth(Double.MAX_VALUE);
            pair.getChildren().add(right);
        }

        container.getChildren().add(pair);
    }
}
     addScrollPane(container, 550, true);
}

    private void displaySwissSystem() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #040D43;");
        Label title = new Label("SWISS SYSTEM TOURNAMENT");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#FFD862"));
        container.getChildren().add(title);

        GridPane grid = makeStandingsGrid(new String[]{"Rank","Team","Wins","Losses","Opp Score","Points"}, "#040D43");
        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));
        sorted.sort((a, b) -> {
            int w = Integer.compare(b.getWins(), a.getWins());
            return w != 0 ? w : Integer.compare(b.getPointsAllowed(), a.getPointsAllowed());
        });
        for (int i = 0; i < sorted.size(); i++) {
            Team t = sorted.get(i);
            addStandingsRow(grid, i + 1, new String[]{
                String.valueOf(i + 1), t.getName(),
                String.valueOf(t.getWins()), String.valueOf(t.getLosses()),
                String.valueOf(t.getPointsAllowed()), String.valueOf(t.getPointsScored())
            });
        }
        container.getChildren().add(grid);

        Label pLabel = new Label("CURRENT ROUND PAIRINGS");
        pLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        pLabel.setTextFill(Color.web("#FFBA09"));
        pLabel.setPadding(new Insets(8, 0, 2, 0));
        container.getChildren().add(pLabel);
        List<Match> pending = tournament.getPendingMatches();
        if (pending.isEmpty()) {
        Label done = new Label("All rounds complete!");
        done.setFont(Font.font("Arial", 12));
        done.setTextFill(Color.web("#7F8EE3"));
        container.getChildren().add(done);
        } else {
        for (int i = 0; i < pending.size(); i += 2) {
        HBox pair = new HBox(8);
        pair.setFillHeight(true);

        HBox left = createMatchResultRow(pending.get(i));
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        pair.getChildren().add(left);

        if (i + 1 < pending.size()) {
            HBox right = createMatchResultRow(pending.get(i + 1));
            HBox.setHgrow(right, Priority.ALWAYS);
            right.setMaxWidth(Double.MAX_VALUE);
            pair.getChildren().add(right);
        }

        container.getChildren().add(pair);
        }
    }
        addScrollPane(container, 550, true);
}

    private void displayFreeForAll() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #040D43;");

        Label title = new Label("FREE FOR ALL - Leaderboard");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#FFD862"));
        container.getChildren().add(title);

        // Standings grid
        GridPane grid = makeStandingsGrid(new String[]{"Rank","Team","Wins","Losses","Pts Scored","Pts Allowed"}, "#040D43");
        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));
        sorted.sort((a, b) -> {
            int wDiff = Integer.compare(b.getWins(), a.getWins());
            if (wDiff != 0) return wDiff;
            return Integer.compare(b.getPointsScored(), a.getPointsScored());
        });
        for (int i = 0; i < sorted.size(); i++) {
            Team t = sorted.get(i);
            addStandingsRow(grid, i + 1, new String[]{
                String.valueOf(i + 1), t.getName(),
                String.valueOf(t.getWins()), String.valueOf(t.getLosses()),
                String.valueOf(t.getPointsScored()), String.valueOf(t.getPointsAllowed())
            });
        }
        container.getChildren().add(grid);

        // Progress summary
        int totalM = tournament.getAllMatches().size(), doneM = 0;
        for (Match m : tournament.getAllMatches()) if (m.isCompleted()) doneM++;
        Label progLbl = new Label("Matches: " + doneM + " / " + totalM + " completed");
        progLbl.setFont(Font.font("Arial", 12));
        progLbl.setTextFill(Color.web("#7F8EE3"));
        progLbl.setPadding(new Insets(0, 0, 4, 0));
        container.getChildren().add(progLbl);

        // Matches grouped by round
        int totalRounds = tournament.getTotalRounds();
        for (int r = 1; r <= totalRounds; r++) {
        List<Match> roundMatches = tournament.getMatchesByRound(r);
        if (roundMatches.isEmpty()) continue;

        Label roundLbl = new Label("Round " + r);
        roundLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        roundLbl.setTextFill(Color.web("#FFBA09"));
        roundLbl.setPadding(new Insets(8, 0, 2, 0));
        container.getChildren().add(roundLbl);

        for (int i = 0; i < roundMatches.size(); i += 2) {
        HBox pair = new HBox(8);
        pair.setFillHeight(true);

        HBox left = createMatchResultRow(roundMatches.get(i));
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        pair.getChildren().add(left);

        if (i + 1 < roundMatches.size()) {
            HBox right = createMatchResultRow(roundMatches.get(i + 1));
            HBox.setHgrow(right, Priority.ALWAYS);
            right.setMaxWidth(Double.MAX_VALUE);
            pair.getChildren().add(right);
        }

        container.getChildren().add(pair);
    }
}

        addScrollPane(container, 550, true);
            }

    private GridPane makeStandingsGrid(String[] headers, String headerColor) {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(4);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: #152055; -fx-border-color: #7F8EE3; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");
        for (int i = 0; i < headers.length; i++) {
            Label h = new Label(headers[i]);
            h.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            h.setStyle("-fx-background-color:" + headerColor + ";-fx-text-fill:#FFD862;-fx-padding:5 8;-fx-background-radius:3;");
            grid.add(h, i, 0);
        }
        return grid;
    }

    private void addStandingsRow(GridPane grid, int rowIndex, String[] values) {
        String rowBg = (rowIndex % 2 == 0) ? "#0d1a40" : "#152055";
        for (int i = 0; i < values.length; i++) {
            Label cell = new Label(values[i]);
            cell.setFont(Font.font("Arial", i == 1 ? FontWeight.BOLD : FontWeight.NORMAL, 11));
            cell.setTextFill(Color.web(i == 0 ? "#FFBA09" : "#E0E6ED"));
            cell.setStyle("-fx-background-color:" + rowBg + ";-fx-padding:4 8;");
            cell.setPrefWidth(i == 1 ? 130 : 60);
            grid.add(cell, i, rowIndex);
        }
    }

    // =========================================================================
    // MATCH ROW HELPERS
    // =========================================================================

    private HBox createMatchResultRow(Match match) {
    String normalStyle = "-fx-background-color: #152055; -fx-border-color: #7F8EE3; -fx-border-width: 0 0 1 0; -fx-cursor: default;";
    String hoverStyle  = "-fx-background-color: #1e3060; -fx-border-color: #FFBA09;  -fx-border-width: 0 0 1 0; -fx-cursor: hand;";

    HBox row = new HBox(15);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(6, 10, 6, 10));
    row.setStyle(normalStyle);

    String t1 = match.getTeam1() != null ? match.getTeam1().getName() : "TBD";
    String t2 = match.getTeam2() != null ? match.getTeam2().getName() : "TBD";

    Label matchLabel = new Label(t1 + " vs " + t2);
    matchLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
    matchLabel.setTextFill(Color.web("#E0E6ED"));
    matchLabel.setPrefWidth(200);

    Label resultLabel = new Label();
    resultLabel.setFont(Font.font("Arial", 12));
    if (match.isCompleted() && match.getWinner() != null) {
        resultLabel.setText("WINNER: " + match.getWinner().getName() + " (" + match.getScore() + ")");
        resultLabel.setTextFill(Color.web("#FFBA09"));
    } else {
        resultLabel.setText("PENDING  ›");
        resultLabel.setTextFill(Color.web("#e74c3c"));
    }

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    row.getChildren().addAll(matchLabel, spacer, resultLabel);

    if (!match.isCompleted()) {
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e  -> row.setStyle(normalStyle));
        row.setOnMouseClicked(e -> showScoreDialog(match));
    }

    return row;
}

    private void addChampionDisplay(Team champion) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #152055;-fx-border-color: #7F8EE3  09; -fx-border-radius: 8; -fx-padding: 15;");
        Label lbl = new Label("CHAMPION: " + champion.getName() + "");
        lbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        lbl.setTextFill(Color.web("#FFD862"));
        box.getChildren().add(lbl);
        bracketView.getChildren().add(box);
    }

    // =========================================================================
    // SCORE DIALOG
    // =========================================================================

    private void showScoreDialog(Match match) {
        if (match.isCompleted()) { showAlert("Already Completed", "This match has already been reported."); return; }
        if (match.getTeam1() == null || match.getTeam2() == null) { showAlert("Not Ready", "Both teams are not assigned yet."); return; }

        Stage dlg = new Stage();
        dlg.setTitle("Report Match Result");
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background-color: #040D43;");

        Label titleLbl = new Label(match.getTeam1().getName() + " vs " + match.getTeam2().getName());
        titleLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLbl.setTextFill(Color.web("#FFD862"));

        TextField s1 = new TextField(); s1.setPromptText("Score"); s1.setPrefWidth(80);
        TextField s2 = new TextField(); s2.setPromptText("Score"); s2.setPrefWidth(80); 
        s1.setStyle("-fx-background-color: #152055; -fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #ffffffa2; -fx-border-color: #7F8EE3; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6;");
        s2.setStyle("-fx-background-color: #152055; -fx-text-fill: #FFFFFF; -fx-prompt-text-fill: #ffffffa2; -fx-border-color: #7F8EE3; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6;");

        // Dynamic winner preview label (no dropdown — winner is always highest scorer)
        Label winnerPreview = new Label("Enter the scores");
        winnerPreview.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        winnerPreview.setTextFill(Color.web("#FFD862"));
        

        HBox r1b = new HBox(10, new Label(match.getTeam1().getName() + ":") {{ setTextFill(Color.WHITE); setFont(Font.font("Arial", FontWeight.BOLD, 13)); }}, s1); r1b.setAlignment(Pos.CENTER);
        HBox r2b = new HBox(10, new Label(match.getTeam2().getName() + ":") {{ setTextFill(Color.WHITE); setFont(Font.font("Arial", FontWeight.BOLD, 13)); }}, s2); r2b.setAlignment(Pos.CENTER);

        Button submit = new Button("Submit");
        submit.setStyle("-fx-background-color: #7F8EE3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
        submit.setDisable(true);
        Button cancel = new Button("Cancel");
        cancel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
        HBox btnBox = new HBox(20, submit, cancel); btnBox.setAlignment(Pos.CENTER);

        vbox.getChildren().addAll(titleLbl, r1b, r2b, winnerPreview, btnBox);
        dlg.setScene(new Scene(vbox, 350, 270));

        // Auto-determine winner by score as user types — no dropdown needed
            javafx.beans.value.ChangeListener<String> scoreListener = (obs, oldVal, newVal) -> {
            try {
                String t1 = s1.getText().trim(), t2 = s2.getText().trim();
                if (!t1.isEmpty() && !t2.isEmpty()) {
                    int sc1 = Integer.parseInt(t1), sc2 = Integer.parseInt(t2);
                    if (sc1 > sc2) {
                        winnerPreview.setText("Winner: " + match.getTeam1().getName());
                        winnerPreview.setStyle("-fx-text-fill: #FFBA09;");
                        submit.setDisable(false);
                    } else if (sc2 > sc1) {
                        winnerPreview.setText("Winner: " + match.getTeam2().getName());
                        winnerPreview.setStyle("-fx-text-fill: #FFBA09;");
                        submit.setDisable(false);
                    } else {
                        winnerPreview.setText("No ties Allowed");
                        winnerPreview.setStyle("-fx-text-fill: #e74c3c;");
                        submit.setDisable(true);
                    }
                } else {
                    winnerPreview.setText("Enter the scores");
                    winnerPreview.setStyle("-fx-text-fill: #FFD862;");
                    submit.setDisable(true);
                }
            } catch (NumberFormatException ignored) {
                winnerPreview.setText("Enter valid numbers");
                winnerPreview.setStyle("-fx-text-fill: #e74c3c;");
                submit.setDisable(true);
            }
};
        s1.textProperty().addListener(scoreListener);
        s2.textProperty().addListener(scoreListener);

        submit.setOnAction(e -> {
            try {
                if (s1.getText().isEmpty() || s2.getText().isEmpty()) { showAlert("Error", "Please enter both scores!"); return; }
                int score1 = Integer.parseInt(s1.getText()), score2 = Integer.parseInt(s2.getText());
                if (score1 == score2) { showAlert("Error", "Scores cannot be tied — one team must win!"); return; }
                // Winner is always determined by higher score — no dropdown needed
                Team w = score1 > score2 ? match.getTeam1() : match.getTeam2();
                pushUndoState();
                tournament.recordWinner(match, w, score1, score2);
                updateBracketView(); updateProgress();
                dlg.close();
            } catch (NumberFormatException ex) { showAlert("Error", "Please enter valid numbers for scores!"); }
        });
        cancel.setOnAction(e -> dlg.close());
        dlg.showAndWait();
    }

    // =========================================================================
    // SAVE / LOAD
    // =========================================================================

    private void saveBracket() {
        String name = bracketNameField.getText();
        File saveDir = new File("saved_brackets");
        if (!saveDir.exists()) saveDir.mkdir();
        File saveFile = new File(saveDir, name.replaceAll("\\s+", "_") + ".txt");
        try (PrintWriter writer = new PrintWriter(saveFile)) {
            writer.println("BRACKET NAME: " + name);
            writer.println("BRACKET TYPE: " + bracketTypeCombo.getValue());
            writer.println("SPORT/GAME: " + sportField.getText());
            writer.println("DESCRIPTION: " + descriptionArea.getText());
            writer.println("STATUS: " + statusLabel.getText());
            writer.println("TEAMS: " + teams.length);
            writer.println("----------------------------------------");
            for (Team t : teams)
                writer.println("Team: " + t.getName() + " | Wins: " + t.getWins() + " | Losses: " + t.getLosses());
            writer.println("----------------------------------------");
            writer.println("MATCH RESULTS:");
            if (tournament != null) for (Match m : tournament.getAllMatches()) {
                if (m.isCompleted()) {
                    String t1 = m.getTeam1() != null ? m.getTeam1().getName() : "TBD";
                    String t2 = m.getTeam2() != null ? m.getTeam2().getName() : "TBD";
                    writer.println(t1 + " vs " + t2 + " -> Winner: " + m.getWinner().getName() + " (" + m.getScore() + ")");
                }
            }
            showAlert("Saved", "Bracket '" + name + "' saved to:\n" + saveFile.getAbsolutePath());
        } catch (FileNotFoundException e) { showAlert("Save Error", "Could not save: " + e.getMessage()); }
    }

    private void loadBracket() {
        File saveDir = new File("saved_brackets");
        if (!saveDir.exists() || saveDir.listFiles() == null || saveDir.listFiles().length == 0) {
            showAlert("No Brackets", "No saved brackets found."); return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Load Bracket");
        fc.setInitialDirectory(saveDir);
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File selected = fc.showOpenDialog(null);
        if (selected != null) {
            try (java.util.Scanner sc = new java.util.Scanner(selected)) {
                String bracketName = "", bracketType = "Single Elimination", sport = "";
                StringBuilder desc = new StringBuilder();
                List<String> teamNames = new ArrayList<>();
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if      (line.startsWith("BRACKET NAME: ")) bracketName = line.substring(14);
                    else if (line.startsWith("BRACKET TYPE: ")) bracketType = line.substring(14);
                    else if (line.startsWith("SPORT/GAME: "))   sport = line.substring(12);
                    else if (line.startsWith("DESCRIPTION: "))  desc.append(line.substring(13));
                    else if (line.startsWith("Team: ")) {
                        String tName = line.substring(6);
                        int idx = tName.indexOf(" |");
                        if (idx > 0) tName = tName.substring(0, idx);
                        teamNames.add(tName);
                    }
                }
                teams = new Team[teamNames.size()];
                for (int i = 0; i < teamNames.size(); i++) teams[i] = new Team(i, teamNames.get(i));
                bracketNameField.setText(bracketName);
                bracketTypeCombo.setValue(bracketType);
                sportField.setText(sport);
                descriptionArea.setText(desc.toString());
                rebuildTournament();
                updateBracketView();
                updateParticipantsList();
                updateProgress();
                showAlert("Loaded", "Successfully loaded: " + selected.getName());
            } catch (FileNotFoundException e) { showAlert("Load Error", "Could not load file: " + e.getMessage()); }
        }
    }

    // =========================================================================
    // PROGRESS & UTILITIES
    // =========================================================================

    private void updateProgress() {
        if (teams.length < 2 || tournament == null || tournament.getAllMatches().isEmpty()) {
            progressBar.setProgress(0); progressLabel.setText("0% Complete");
            statusLabel.setText("PENDING");
            statusLabel.setStyle("-fx-font-weight:bold;-fx-font-size:16px;-fx-text-fill:#e74c3c;");
            return;
        }
        int total = tournament.getAllMatches().size(), done = 0;
        for (Match m : tournament.getAllMatches()) if (m.isCompleted()) done++;
        double progress = (double) done / total;
        progressBar.setProgress(progress);
        progressLabel.setText(String.format("%d%% Complete", (int)(progress * 100)));
        if (tournament.getTournamentWinner() != null) {
            statusLabel.setText("COMPLETE");
            statusLabel.setStyle("-fx-font-weight:bold;-fx-font-size:16px;-fx-text-fill:#FFBA09;");
        } else {
            statusLabel.setText("PENDING");
            statusLabel.setStyle("-fx-font-weight:bold;-fx-font-size:16px;-fx-text-fill:#e74c3c;");
        }
    }

    private void drawBracketLine(GraphicsContext gc, Match child, Map<Match, Double> matchY,
            double srcRX, double midX, double dstLX, double targetY, double cardMid) {
        if (child == null || !matchY.containsKey(child)) return;
        double y = matchY.get(child) + cardMid;
        gc.strokeLine(srcRX, y, midX, y);
        gc.strokeLine(midX, y, midX, targetY);
        gc.strokeLine(midX, targetY, dstLX, targetY);
    }

   private void addScrollPane(Pane content, double prefHeight) {
    addScrollPane(content, prefHeight, false);  // brackets: don't fit to width
}

private void addScrollPane(javafx.scene.Node content, double prefHeight, boolean fitToWidth) {
    bracketView.setStyle(
        "-fx-background-color: #040D43;" +
        "-fx-focus-color: transparent;" +
        "-fx-faint-focus-color: transparent;"
    );

    ScrollPane sp = new ScrollPane(content);
    sp.setFitToWidth(fitToWidth);
    sp.setFitToHeight(false);
    sp.setPrefHeight(prefHeight);
    sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    sp.setHbarPolicy(fitToWidth ? ScrollPane.ScrollBarPolicy.NEVER : ScrollPane.ScrollBarPolicy.AS_NEEDED);
    sp.setStyle(
        "-fx-background: #040D43;" +
        "-fx-background-color: #040D43;" +
        "-fx-border-color: #7F8EE3;" +
        "-fx-border-width: 1;" +
        "-fx-border-radius: 5;" +
        "-fx-focus-color: transparent;" +
        "-fx-faint-focus-color: transparent;"
    );
    sp.getStylesheets().clear();
    sp.setFocusTraversable(false);
    sp.skinProperty().addListener((obs, oldSkin, newSkin) -> {
        sp.lookupAll(".scroll-bar").forEach(node ->
            node.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".thumb").forEach(node ->
            node.setStyle("-fx-background-color: #7F8EE3; -fx-background-radius: 0;"));
        sp.lookupAll(".track").forEach(node ->
            node.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".increment-button, .decrement-button").forEach(node ->
            node.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".increment-arrow, .decrement-arrow").forEach(node ->
            node.setStyle("-fx-background-color: transparent;"));
        javafx.scene.Node viewport = sp.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: #040D43; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            viewport.focusedProperty().addListener((fo, wf, nf) ->
                viewport.setStyle("-fx-background-color: #040D43; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"));
        }
    });
    sp.setHvalue(savedScrollH);
    sp.setVvalue(savedScrollV);
    sp.hvalueProperty().addListener((obs, oldVal, newVal) -> savedScrollH = newVal.doubleValue());
    sp.vvalueProperty().addListener((obs, oldVal, newVal) -> savedScrollV = newVal.doubleValue());
    bracketView.getChildren().clear();
    bracketView.getChildren().add(sp);
    VBox.setVgrow(sp, Priority.ALWAYS);
}

    private void addPaneLabel(Pane pane, String text, double x, double y, String color, FontWeight weight, int size) {
        Label lbl = styledLabel(text, color, weight, size);
        lbl.setLayoutX(x); lbl.setLayoutY(y);
        pane.getChildren().add(lbl);
    }

    private Label styledLabel(String text, String color, FontWeight weight, int size) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Arial", weight, size));
        lbl.setTextFill(Color.web(color));
        return lbl;
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        String normal = "-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:8 15;-fx-background-radius:5;";
        String hover  = "-fx-background-color:" + adjustColor(color) + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:8 15;-fx-background-radius:5;";
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(normal));
        return btn;
    }

    private String adjustColor(String hex) {
        if (hex.equals("#7F8EE3")) return "#5a6abf";
        if (hex.equals("#e74c3c")) return "#c0392b";
        return hex;
    }

   // =========================================================================
// SCORE MATRIX — router + per-format implementations
//
// HOW TO APPLY:
//   1. Delete the existing showScoreMatrix() method from app.java.
//   2. Paste ALL of this code in its place (inside the app class body).
// =========================================================================

// ── Router ────────────────────────────────────────────────────────────────
private void showScoreMatrix() {
    if (tournament == null || teams.length == 0) {
        showAlert("Score Matrix", "No tournament data available.");
        return;
    }
    switch (bracketTypeCombo.getValue()) {
        case "Round Robin":
        case "Free For All":
            showGridScoreMatrix();
            break;
        case "Swiss System":
            showSwissScoreMatrix();
            break;
        case "Single Elimination":
        case "Double Elimination":
            showEliminationScoreMatrix();
            break;
        default:
            showGridScoreMatrix();
    }
}

// ── 1. GRID MATRIX  (Round Robin / Free For All) ──────────────────────────
//    Symmetric grid: row = team scored FROM, col = team scored AGAINST.
//    Gold cell = that team won that matchup.
private void showGridScoreMatrix() {
    ScoreMatrix sm = tournament.getScoreMatrix();
    int size = teams.length;

    GridPane grid = new GridPane();
    grid.setHgap(2);
    grid.setVgap(2);
    grid.setPadding(new Insets(4));

    String headerStyle =
        "-fx-background-color: #1a2a6c;" +
        "-fx-text-fill: #FFD862;" +
        "-fx-font-weight: bold;" +
        "-fx-font-size: 11px;" +
        "-fx-padding: 5 8;" +
        "-fx-alignment: CENTER;" +
        "-fx-border-color: #7F8EE3;" +
        "-fx-border-width: 0 0 1 0;";
    String diagStyle =
        "-fx-background-color: #0a1540;" +
        "-fx-text-fill: #7F8EE3;" +
        "-fx-font-size: 11px;" +
        "-fx-padding: 5 8;" +
        "-fx-alignment: CENTER;";
    String playedStyle =
        "-fx-background-color: #152055;" +
        "-fx-text-fill: #E0E6ED;" +
        "-fx-font-size: 11px;" +
        "-fx-padding: 5 8;" +
        "-fx-alignment: CENTER;";
    String pendingStyle =
        "-fx-background-color: #152055;" +
        "-fx-text-fill: #4a5a8a;" +
        "-fx-font-size: 11px;" +
        "-fx-padding: 5 8;" +
        "-fx-alignment: CENTER;";

    // Corner
    Label corner = new Label("");
    corner.setMinWidth(70);
    corner.setStyle(headerStyle);
    grid.add(corner, 0, 0);

    // Column headers
    for (int j = 0; j < size; j++) {
        String name = teams[j].getName();
        if (name.length() > 7) name = name.substring(0, 7);
        Label hdr = new Label(name);
        hdr.setMinWidth(55); hdr.setMaxWidth(55);
        hdr.setWrapText(false);
        hdr.setStyle(headerStyle);
        hdr.setAlignment(Pos.CENTER);
        grid.add(hdr, j + 1, 0);
    }

    // Data rows
    for (int i = 0; i < size; i++) {
        String rname = teams[i].getName();
        if (rname.length() > 9) rname = rname.substring(0, 9);
        Label rowHdr = new Label(rname);
        rowHdr.setMinWidth(70);
        rowHdr.setStyle(headerStyle);
        grid.add(rowHdr, 0, i + 1);

        for (int j = 0; j < size; j++) {
            Label cell;
            if (i == j) {
                cell = new Label("—");
                cell.setStyle(diagStyle);
            } else {
                int score = sm.getScore(i, j);
                if (score == -1) {
                    cell = new Label("?");
                    cell.setStyle(pendingStyle);
                } else {
                    cell = new Label(String.valueOf(score));
                    int opp = sm.getScore(j, i);
                    boolean won = score > opp;
                    cell.setStyle(playedStyle +
                        (won ? "-fx-text-fill: #FFBA09; -fx-font-weight: bold;" : ""));
                }
            }
            cell.setMinWidth(55); cell.setMaxWidth(55);
            cell.setAlignment(Pos.CENTER);
            grid.add(cell, j + 1, i + 1);
        }
    }

    ScrollPane sp = buildStyledScrollPane(grid, 540, 300);

    // Legend
    Label legendWon     = new Label("■ Won");
    legendWon.setStyle("-fx-text-fill: #FFBA09; -fx-font-size: 10px; -fx-font-weight: bold;");
    Label legendPlayed  = new Label("■ Score recorded");
    legendPlayed.setStyle("-fx-text-fill: #E0E6ED; -fx-font-size: 10px;");
    Label legendPending = new Label("? Not yet played");
    legendPending.setStyle("-fx-text-fill: #4a5a8a; -fx-font-size: 10px;");
    HBox legend = new HBox(16, legendWon, legendPlayed, legendPending);
    legend.setAlignment(Pos.CENTER);

    Label titleLbl = buildMatrixTitle(bracketTypeCombo.getValue().toUpperCase() + " SCORE MATRIX");
    Label subtitleLbl = buildMatrixSubtitle("Row = team  ·  Column = opponent  ·  Gold = winner");
    Button okBtn      = buildMatrixOkButton();

    VBox layout = buildMatrixLayout(titleLbl, subtitleLbl, sp, legend, okBtn);

    int dynW = Math.min(900, 120 + size * 55);
    int dynH = Math.min(600, 200 + size * 30);
    openMatrixStage("Score Matrix", layout, dynW, dynH);
}

// ── 2. SWISS ROUND-BY-ROUND TABLE ────────────────────────────────────────
//    Shows every completed round as a titled block of match rows,
//    then lists pending pairings for the current round.
private void showSwissScoreMatrix() {
    VBox container = new VBox(12);
    container.setPadding(new Insets(4));
    container.setStyle("-fx-background-color: #040D43;");

    ScrollPane sp = buildStyledScrollPane(container, 520, 360);
    sp.setFitToWidth(true);

    Label titleLbl    = buildMatrixTitle("SWISS — Round Results");
    Label subtitleLbl = buildMatrixSubtitle("All completed rounds  ·  Click a pending match to report");
    Button okBtn      = buildMatrixOkButton();
    VBox layout       = buildMatrixLayout(titleLbl, subtitleLbl, sp, new HBox(), okBtn);

    Stage stage = new Stage();
    stage.setTitle("Swiss Score Matrix");
    stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
    Scene scene = new Scene(layout, 520, 500);
    scene.setFill(Color.web("#040D43"));
    stage.setScene(scene);
    stage.setResizable(true);

    Runnable[] refreshRef = { null };
    refreshRef[0] = () -> {
        container.getChildren().clear();

        int totalRounds = tournament.getTotalRounds();
        boolean anyCompleted = false;
        for (int r = 1; r <= totalRounds; r++) {
            List<Match> roundMatches = tournament.getMatchesByRound(r);
            List<Match> completed = new ArrayList<>();
            for (Match m : roundMatches) if (m.isCompleted()) completed.add(m);
            if (completed.isEmpty()) continue;
            anyCompleted = true;

            Label roundLbl = new Label("Round " + r + " Results");
            roundLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            roundLbl.setTextFill(Color.web("#FFBA09"));
            roundLbl.setPadding(new Insets(4, 0, 2, 0));
            container.getChildren().add(roundLbl);

            for (Match m : completed)
                container.getChildren().add(buildSwissMatchRow(m, stage, refreshRef[0]));
        }

        if (!anyCompleted) {
            Label none = new Label("No completed matches yet.");
            none.setStyle("-fx-text-fill: #7F8EE3; -fx-font-size: 12px;");
            container.getChildren().add(none);
        }

        List<Match> pending = tournament.getPendingMatches();
        if (!pending.isEmpty()) {
            Label pendingLbl = new Label("Current Round Pairings");
            pendingLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            pendingLbl.setTextFill(Color.web("#7F8EE3"));
            pendingLbl.setPadding(new Insets(10, 0, 2, 0));
            container.getChildren().add(pendingLbl);
            for (Match m : pending)
                container.getChildren().add(buildSwissMatchRow(m, stage, refreshRef[0]));
        }
    };

    refreshRef[0].run(); // initial population
    stage.showAndWait();
}

private HBox buildSwissMatchRow(Match m, Stage stage, Runnable refresh) {
    String normalStyle = "-fx-background-color: #152055; -fx-border-color: #1a2a6c; -fx-border-width: 0 0 1 0;";
    String hoverStyle  = "-fx-background-color: #1e3060; -fx-border-color: #FFBA09; -fx-border-width: 0 0 1 0; -fx-cursor: hand;";

    HBox row = new HBox(10);
    row.setPadding(new Insets(5, 10, 5, 10));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setStyle(normalStyle);

    String t1 = m.getTeam1() != null ? m.getTeam1().getName() : "TBD";
    String t2 = m.getTeam2() != null ? m.getTeam2().getName() : "TBD";
    boolean done = m.isCompleted();
    String winnerName = (done && m.getWinner() != null) ? m.getWinner().getName() : null;

    Label lbl1 = new Label(t1);
    lbl1.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    lbl1.setPrefWidth(130);
    lbl1.setTextFill(Color.web(t1.equals(winnerName) ? "#FFBA09" : "#E0E6ED"));

    Label vs = new Label("vs");
    vs.setStyle("-fx-text-fill: #4a5a8a; -fx-font-size: 11px;");

    Label lbl2 = new Label(t2);
    lbl2.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    lbl2.setPrefWidth(130);
    lbl2.setTextFill(Color.web(t2.equals(winnerName) ? "#FFBA09" : "#E0E6ED"));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Label result = new Label();
    result.setFont(Font.font("Arial", 11));
    if (done && m.getScore() != null) {
        result.setText(m.getScore());
        result.setStyle("-fx-text-fill: #FFD862;");
    } else {
        result.setText("PENDING ›");
        result.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10px;");
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e  -> row.setStyle(normalStyle));
        row.setOnMouseClicked(e -> {
            showScoreDialog(m);
            updateBracketView();
            updateProgress();
            if (refresh != null) refresh.run(); // ← rebuilds in-place
        });
    }

    row.getChildren().addAll(lbl1, vs, lbl2, spacer, result);
    return row;
}

private void showEliminationScoreMatrix() {
    VBox container = new VBox(10);
    container.setPadding(new Insets(4));
    container.setStyle("-fx-background-color: #040D43;");

    String type = bracketTypeCombo.getValue();
    boolean isDE = "Double Elimination".equals(type);

    ScrollPane sp = buildStyledScrollPane(container, 520, 400);
    sp.setFitToWidth(true);

    String subtitle = isDE
        ? "Winners & Losers brackets  ·  Click a pending match to report"
        : "All rounds  ·  Click a pending match to report";

    Label titleLbl    = buildMatrixTitle(isDE ? "DOUBLE ELIMINATION — Match Results" : "SINGLE ELIMINATION — Match Results");
    Label subtitleLbl = buildMatrixSubtitle(subtitle);
    Button okBtn      = buildMatrixOkButton();
    VBox layout       = buildMatrixLayout(titleLbl, subtitleLbl, sp, new HBox(), okBtn);

    Stage stage = new Stage();
    stage.setTitle("Match Results");
    stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
    Scene scene = new Scene(layout, 520, 560);
    scene.setFill(Color.web("#040D43"));
    stage.setScene(scene);
    stage.setResizable(true);

    // Wire refresh into the actual Runnable now that it's defined
    Runnable[] refreshRef = { null };
    refreshRef[0] = () -> {
        container.getChildren().clear();
        if (isDE) {
            addElimSection(container, "WINNERS BRACKET", getDeWinnersMatches(), true, stage, refreshRef[0]);
            addElimSection(container, "LOSERS BRACKET", tournament.getLosersBracketMatches(), false, stage, refreshRef[0]);
            Match gf = tournament.getGrandFinals();
            if (gf != null) {
                Label gfLbl = new Label("GRAND FINAL");
                gfLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                gfLbl.setTextFill(Color.web("#FFBA09"));
                gfLbl.setPadding(new Insets(8, 0, 2, 0));
                container.getChildren().add(gfLbl);
                container.getChildren().add(buildElimMatchRow(gf, stage, refreshRef[0]));
            }
        } else {
            int totalRounds = tournament.getTotalRounds();
            for (int r = 1; r <= totalRounds; r++) {
                List<Match> ms = tournament.getMatchesByRound(r);
                if (ms.isEmpty()) continue;
                int fromEnd = totalRounds - r;
                String rName = fromEnd == 0 ? "Championship" : fromEnd == 1 ? "Finals"
                             : fromEnd == 2 ? "Semifinals"   : fromEnd == 3 ? "Quarterfinals"
                             : "Round " + r;
                Label rLbl = new Label(rName);
                rLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                rLbl.setTextFill(Color.web("#FFBA09"));
                rLbl.setPadding(new Insets(6, 0, 2, 0));
                container.getChildren().add(rLbl);
                for (Match m : ms) {
                    boolean isBye = m.isCompleted() && (m.getTeam1() == null || m.getTeam2() == null);
                    if (isBye) continue;
                    container.getChildren().add(buildElimMatchRow(m, stage, refreshRef[0]));
                }
            }
        }
    };

    refreshRef[0].run(); // initial population
    stage.showAndWait();
}

private void addElimSection(VBox container, String sectionTitle, List<Match> matches,
                             boolean isWinners, Stage stage, Runnable refresh) {
    if (matches == null || matches.isEmpty()) return;

    Label secLbl = new Label(sectionTitle);
    secLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
    secLbl.setTextFill(Color.web("#FFBA09"));
    secLbl.setPadding(new Insets(8, 0, 2, 0));
    container.getChildren().add(secLbl);

    Map<Integer, List<Match>> byRound = new java.util.LinkedHashMap<>();
    for (Match m : matches)
        byRound.computeIfAbsent(m.getRound(), k -> new ArrayList<>()).add(m);

    List<Integer> rounds = new ArrayList<>(byRound.keySet());
    Collections.sort(rounds);
    int totalRoundsInSection = rounds.size();

    for (int ri = 0; ri < rounds.size(); ri++) {
        int r = rounds.get(ri);
        List<Match> ms = byRound.get(r);
        int fromEnd = totalRoundsInSection - 1 - ri;
        String rName = isWinners
            ? (fromEnd == 0 ? "Upper Final" : fromEnd == 1 ? "Upper Semis" : "Upper Round " + r)
            : (fromEnd == 0 ? "Losers Final" : "Losers Round " + (ri + 1));

        Label rLbl = new Label(rName);
        rLbl.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        rLbl.setTextFill(Color.web("#7F8EE3"));
        rLbl.setPadding(new Insets(4, 0, 1, 8));
        container.getChildren().add(rLbl);

        for (Match m : ms) {
            boolean isBye = m.isCompleted() && (m.getTeam1() == null || m.getTeam2() == null);
            if (isBye) continue;
            container.getChildren().add(buildElimMatchRow(m, stage, refresh));
        }
    }
}

private HBox buildElimMatchRow(Match m, Stage stage, Runnable refresh) {
    String normalStyle = "-fx-background-color: #152055; -fx-border-color: #1a2a6c; -fx-border-width: 0 0 1 0;";
    String hoverStyle  = "-fx-background-color: #1e3060; -fx-border-color: #FFBA09; -fx-border-width: 0 0 1 0; -fx-cursor: hand;";

    HBox row = new HBox(10);
    row.setPadding(new Insets(5, 10, 5, 10));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setStyle(normalStyle);

    String t1 = m.getTeam1() != null ? m.getTeam1().getName() : "TBD";
    String t2 = m.getTeam2() != null ? m.getTeam2().getName() : "TBD";
    boolean done = m.isCompleted();
    String winnerName = (done && m.getWinner() != null) ? m.getWinner().getName() : null;

    Label lbl1 = new Label(t1);
    lbl1.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    lbl1.setPrefWidth(130);
    lbl1.setTextFill(Color.web(t1.equals(winnerName) ? "#FFBA09" : "#E0E6ED"));

    Label vs = new Label("vs");
    vs.setStyle("-fx-text-fill: #4a5a8a; -fx-font-size: 11px;");

    Label lbl2 = new Label(t2);
    lbl2.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    lbl2.setPrefWidth(130);
    lbl2.setTextFill(Color.web(t2.equals(winnerName) ? "#FFBA09" : "#E0E6ED"));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Label result = new Label();
    result.setFont(Font.font("Arial", 11));
    if (done && m.getScore() != null) {
        result.setText(m.getScore());
        result.setStyle("-fx-text-fill: #FFD862;");
    } else if (!t1.equals("TBD") && !t2.equals("TBD")) {
        result.setText("PENDING ›");
        result.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10px;");
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e  -> row.setStyle(normalStyle));
        row.setOnMouseClicked(e -> {
            showScoreDialog(m);
            updateBracketView();
            updateProgress();
            if (refresh != null) refresh.run(); // ← rebuilds list in-place
        });
    } else {
        result.setText("TBD");
        result.setStyle("-fx-text-fill: #4a5a8a; -fx-font-size: 10px;");
    }

    row.getChildren().addAll(lbl1, vs, lbl2, spacer, result);
    return row;
}

private List<Match> getDeWinnersMatches() {
    List<Match> all = new ArrayList<>();
    for (int r = 1; r <= 10; r++) { 
        List<Match> ms = tournament.getWinnersMatchesByRound(r);
        if (ms == null || ms.isEmpty()) break;
        all.addAll(ms);
    }
    return all;
}

private Label buildMatrixTitle(String text) {
    Label lbl = new Label(text);
    lbl.setFont(Font.font("Arial", FontWeight.BOLD, 14));
    lbl.setTextFill(Color.web("#FFD862"));
    return lbl;
}

private Label buildMatrixSubtitle(String text) {
    Label lbl = new Label(text);
    lbl.setFont(Font.font("Arial", 10));
    lbl.setTextFill(Color.web("#7F8EE3"));
    return lbl;
}

private Button buildMatrixOkButton() {
    String baseStyle =
        "-fx-background-color: #FFBA09; -fx-text-fill: #040D43;" +
        "-fx-font-weight: bold; -fx-padding: 6 28;" +
        "-fx-background-radius: 5; -fx-cursor: hand;";
    String hoverStyle =
        "-fx-background-color: #FFBA09; -fx-text-fill: #040D43;" +
        "-fx-font-weight: bold; -fx-padding: 6 28;" +
        "-fx-background-radius: 5; -fx-cursor: hand;";
    Button btn = new Button("OK");
    btn.setStyle(baseStyle);
    btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
    btn.setOnMouseExited(e  -> btn.setStyle(baseStyle));
    btn.setOnAction(e -> ((Stage) btn.getScene().getWindow()).close());
    return btn;
}

private VBox buildMatrixLayout(Label title, Label subtitle, ScrollPane sp, HBox legend, Button okBtn) {
    VBox layout = new VBox(10, title, subtitle, sp, legend, okBtn);
    layout.setAlignment(Pos.CENTER);
    layout.setFillWidth(true);
    layout.setPadding(new Insets(20));
    layout.setStyle("-fx-background-color: #040D43;");
    VBox.setVgrow(sp, Priority.ALWAYS);
    return layout;
}

private ScrollPane buildStyledScrollPane(javafx.scene.Node content, double prefW, double prefH) {
    ScrollPane sp = new ScrollPane(content);
    sp.setFitToWidth(false);
    sp.setFitToHeight(false);
    sp.setPrefSize(prefW, prefH);
    sp.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    VBox.setVgrow(sp, Priority.ALWAYS);
    sp.setStyle(
        "-fx-background: #040D43;" +
        "-fx-background-color: #040D43;" +
        "-fx-border-color: #7F8EE3;" +
        "-fx-border-width: 1;" +
        "-fx-border-radius: 4;"
    );
    sp.skinProperty().addListener((obs, oldSkin, newSkin) -> {
        sp.lookupAll(".scroll-bar").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".thumb").forEach(n ->
            n.setStyle("-fx-background-color: #7F8EE3; -fx-background-radius: 4;"));
        sp.lookupAll(".track").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".increment-button, .decrement-button").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        sp.lookupAll(".increment-arrow, .decrement-arrow").forEach(n ->
            n.setStyle("-fx-background-color: transparent;"));
        javafx.scene.Node vp = sp.lookup(".viewport");
        if (vp != null) vp.setStyle("-fx-background-color: #040D43;");
    });
    return sp;
}

private void openMatrixStage(String title, VBox layout, int width, int height) {
    Stage stage = new Stage();
    stage.setTitle(title);
    stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
    Scene scene = new Scene(layout, width, height);
    scene.setFill(Color.web("#040D43"));
    stage.setScene(scene);
    stage.setResizable(true);
    stage.showAndWait();
}

    private void showAlert(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.NONE);
    alert.setTitle(title);
    alert.getButtonTypes().add(ButtonType.OK);

    DialogPane dialogPane = alert.getDialogPane();
    dialogPane.setMinSize(280, 160);
    dialogPane.setPrefSize(280, 160);
    dialogPane.setMaxSize(280, 160);
    dialogPane.setStyle(
        "-fx-background-color: #040D43;" +
        "-fx-border-color: #7F8EE3;" +
        "-fx-border-width: 2;" +
        "-fx-border-radius: 8;" +
        "-fx-background-radius: 8;"
    );

    javafx.scene.Node nativeButtonBar = dialogPane.lookup(".button-bar");
    if (nativeButtonBar != null) {
        nativeButtonBar.setVisible(false);
        nativeButtonBar.setManaged(false);
    }

    Label contentLabel = new Label(message);
    contentLabel.setMaxWidth(Double.MAX_VALUE);
    contentLabel.setAlignment(Pos.CENTER);
    contentLabel.setStyle(
        "-fx-text-fill: #E0E6ED;" +
        "-fx-font-size: 12px;" +
        "-fx-font-family: Arial;" +
        "-fx-font-weight: bold;"
    );
    contentLabel.setWrapText(true);

    String baseStyle =
        "-fx-background-color: #FFBA09;" +
        "-fx-text-fill: #040D43;" +
        "-fx-font-weight: bold;" +
        "-fx-padding: 6 20;" +
        "-fx-background-radius: 5;" +
        "-fx-cursor: hand;";
    String hoverStyle =
        "-fx-background-color: #FFD862;" +
        "-fx-text-fill: #040D43;" +
        "-fx-font-weight: bold;" +
        "-fx-padding: 6 20;" +
        "-fx-background-radius: 5;" +
        "-fx-cursor: hand;";

    Button okButton = new Button("OK");
    okButton.setStyle(baseStyle);
    okButton.setOnAction(e -> alert.close());
    okButton.setOnMouseEntered(e -> okButton.setStyle(hoverStyle));
    okButton.setOnMouseExited(e -> okButton.setStyle(baseStyle));

    VBox centerLayout = new VBox(40);
    centerLayout.setAlignment(Pos.CENTER);
    centerLayout.setPadding(new Insets(80, 15, 20, 15));
    centerLayout.getChildren().addAll(contentLabel, okButton);
    dialogPane.setContent(centerLayout);
    alert.showAndWait();
}
    private boolean isPowerOfTwo(int n) { return n > 0 && (n & (n - 1)) == 0; }
    public static void main(String[] args) { launch(args); }
}