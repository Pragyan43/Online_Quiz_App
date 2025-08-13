import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Scanner;

public class QuizAppGUI {
    static class Question {
        String questionText;
        String[] options;
        char correctOption;

        Question(String questionText, String[] options, char correctOption) {
            this.questionText = questionText;
            this.options = options;
            this.correctOption = correctOption;
        }
    }

    private JFrame frame;
    private JLabel questionLabel;
    private JRadioButton[] optionButtons;
    private ButtonGroup group;
    private JButton nextButton;
    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private int score = 0;
    private String sessionToken = null;

    public QuizAppGUI() {
        questions = new ArrayList<>();
        requestSessionToken();
        fetchQuestionsFromAPI();

        frame = new JFrame("Java Quiz App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 350);
        frame.getContentPane().setBackground(Color.WHITE);

        questionLabel = new JLabel("", JLabel.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));
        questionLabel.setForeground(new Color(0, 0, 139)); // Dark Blue

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new GridLayout(4, 1));
        optionsPanel.setBackground(Color.WHITE);
        optionButtons = new JRadioButton[4];
        group = new ButtonGroup();

        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JRadioButton();
            optionButtons[i].setForeground(Color.BLACK);
            optionButtons[i].setBackground(Color.WHITE);
            group.add(optionButtons[i]);
            optionsPanel.add(optionButtons[i]);
        }

        nextButton = new JButton("Next");
        nextButton.setForeground(Color.WHITE);
        nextButton.setBackground(new Color(0, 0, 139)); // Dark Blue
        nextButton.addActionListener(e -> checkAnswer());

        frame.setLayout(new BorderLayout());
        frame.add(questionLabel, BorderLayout.NORTH);
        frame.add(optionsPanel, BorderLayout.CENTER);
        frame.add(nextButton, BorderLayout.SOUTH);

        showQuestion();
        frame.setVisible(true);
    }

    private void requestSessionToken() {
        try {
            URL url = new URL("https://opentdb.com/api_token.php?command=request");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder jsonString = new StringBuilder();
            while (scanner.hasNext()) {
                jsonString.append(scanner.nextLine());
            }
            scanner.close();

            JSONObject response = new JSONObject(jsonString.toString());
            sessionToken = response.getString("token");
        } catch (Exception e) {
            sessionToken = null;
        }
    }

    private void fetchQuestionsFromAPI() {
        try {
            String apiUrl = "https://opentdb.com/api.php?amount=10&type=multiple";
            if (sessionToken != null) {
                apiUrl += "&token=" + sessionToken;
            }

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == 429) {
                Thread.sleep(2000); // wait and retry
                fetchQuestionsFromAPI();
                return;
            } else if (responseCode != 200) {
                throw new RuntimeException("HttpResponseCode: " + responseCode);
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder jsonString = new StringBuilder();
            while (scanner.hasNext()) {
                jsonString.append(scanner.nextLine());
            }
            scanner.close();

            JSONObject json = new JSONObject(jsonString.toString());
            JSONArray results = json.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject qObj = results.getJSONObject(i);
                String questionText = htmlDecode(qObj.getString("question"));
                String correct = htmlDecode(qObj.getString("correct_answer"));
                JSONArray incorrect = qObj.getJSONArray("incorrect_answers");

                List<String> options = new ArrayList<>();
                options.add(correct);
                for (int j = 0; j < incorrect.length(); j++) {
                    options.add(htmlDecode(incorrect.getString(j)));
                }
                Collections.shuffle(options);

                char correctOption = (char) ('A' + options.indexOf(correct));
                String[] formattedOptions = new String[4];
                for (int j = 0; j < 4; j++) {
                    formattedOptions[j] = (char) ('A' + j) + ". " + options.get(j);
                }

                questions.add(new Question(questionText, formattedOptions, correctOption));
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Failed to fetch questions from API. Loading fallback questions.", "Error", JOptionPane.ERROR_MESSAGE);
            loadFallbackQuestions();
        }
    }

    private void loadFallbackQuestions() {
        questions.clear();
        questions.add(new Question("What is Java?", new String[]{
                "A. A programming language", "B. An island", "C. A drink", "D. All of the above"
        }, 'D'));
        questions.add(new Question("Which keyword is used to inherit a class?", new String[]{
                "A. this", "B. super", "C. extends", "D. implements"
        }, 'C'));
        questions.add(new Question("Which method starts a Java application?", new String[]{
                "A. main()", "B. start()", "C. run()", "D. init()"
        }, 'A'));
    }

    private String htmlDecode(String text) {
        return text.replaceAll("&quot;", "\"")
                .replaceAll("&#039;", "'")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">");
    }

    private void showQuestion() {
        if (currentQuestionIndex < questions.size()) {
            Question q = questions.get(currentQuestionIndex);
            questionLabel.setText("Q" + (currentQuestionIndex + 1) + ": " + q.questionText);
            for (int i = 0; i < 4; i++) {
                optionButtons[i].setText(q.options[i]);
                optionButtons[i].setActionCommand(Character.toString(q.options[i].charAt(0)));
            }
            group.clearSelection();
        } else {
            String username = JOptionPane.showInputDialog(frame, "Enter your name:");
            storeResultInDatabase(username);
            showResult();
        }
    }

    private void checkAnswer() {
        if (group.getSelection() != null) {
            char selected = group.getSelection().getActionCommand().charAt(0);
            if (selected == questions.get(currentQuestionIndex).correctOption) {
                score++;
            }
            currentQuestionIndex++;
            showQuestion();
        } else {
            JOptionPane.showMessageDialog(frame, "Please select an answer.", "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void storeResultInDatabase(String username) {
        String url = "jdbc:mysql://localhost:3306/quiz_db";
        String user = "root";
        String password = "@Simi123";

        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            String query = "INSERT INTO results (username, score) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setInt(2, score);
            stmt.executeUpdate();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error connecting to the database.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showResult() {
        JOptionPane.showMessageDialog(frame, "Quiz finished! Your score: " + score + "/" + questions.size());
        frame.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QuizAppGUI::new);
    }
}
