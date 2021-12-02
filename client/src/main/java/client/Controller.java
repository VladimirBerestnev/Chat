package client;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;

    private Socket socket;
    private static final int PORT = 60000;
    private DataInputStream in;
    private DataOutputStream out;
    private final String IP_ADDRESS = "localhost";
    private boolean authenticated;
    private String nickname;
    private Stage stage;
    private Stage regStage;
    private RegController regController;

    private void setAuthenticated (boolean authenticated){
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);

        if (!authenticated){
            nickname = "";
        }
        setTitle(nickname);
        textArea.clear();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        Platform.runLater(() -> {
            stage = (Stage) textArea.getScene().getWindow();
            stage.setOnCloseRequest(windowEvent -> {
                System.out.println("bye");
                if (socket != null && !socket.isClosed()){
                    try {
                        out.writeUTF("/end");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        setAuthenticated(false);
    }

    private void connect(){
        try {
            socket = new Socket(IP_ADDRESS,PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")){

                        if (str.equals("/end")){
                            break;
                        }
                            if (str.equals("/reg_ok")){
                                regController.showResult("/reg_ok");
                            }

                            if (str.equals("/reg_no")){
                                regController.showResult("/reg_no");
                            }

                        if (str.startsWith("/auth_ok")){
                            nickname = str.split("\\s+")[1];
                            setAuthenticated(true);
                            try {
                                BufferedReader br = new BufferedReader(new FileReader("chat1.txt"));
                                String line;
                                ArrayList<String> list = new ArrayList<>();

                                while ((line = br.readLine()) != null){
 //                               textArea.appendText(line + "\n");
                                    list.add(line);
                            }
                                int j;
                                if (list.size() < 101){

                                    j = 0;
                                } else { j = list.size() - 100;
                                }
                                    for (int i = j; i < list.size(); i++) {
                                        textArea.appendText(list.get(i) + "\n");
                                    }
                                    break;

                            } catch (IOException e) {

                                e.printStackTrace();
                            }
                            break;
                        }} else {
                            textArea.appendText(str + "\n");
                        }
                    }

                    //цикл работы
                    while (authenticated) {
                        String str = in.readUTF();



                        if (str.startsWith("/")){

                        if (str.equals("/end")){
                            System.out.println("Disconnect");
                            break;
                        }
                            //Обновление списка клиентов
                            if (str.startsWith("/clientList")){
                                String[] token = str.split("\\s+");
                                Platform.runLater(()->{
                                    clientList.getItems().clear();
                                    for (int i = 1; i <token.length ; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            }
                        }else {
                            textArea.appendText(str + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg() {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {

        if (socket == null || socket.isClosed()){
            connect();
    }
        String msg = String.format("/auth %s %s", loginField.getText().trim(), passwordField.getText().trim());

        try {
            out.writeUTF(msg);
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setTitle (String nickname){
        Platform.runLater(() -> {
            if (nickname.equals("")){
                stage.setTitle("Chat");
            } else {
                stage.setTitle(String.format("Chat: [%s]", nickname));
            }
        });
    }

    public void clickClientList(MouseEvent mouseEvent) {
        String receiver = clientList.getSelectionModel().getSelectedItem();
        textField.setText("/w " + receiver + " ");
    }

    private void createRegWindow(){
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();
            regStage = new Stage();
            regStage.setTitle("Registration");
            regStage.setScene(new Scene(root, 400, 320));

            regStage.initModality(Modality.APPLICATION_MODAL);
            regStage.initStyle(StageStyle.UTILITY);

            regController = fxmlLoader.getController();
            regController.setController(this);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToReg(ActionEvent actionEvent) {
        if (regStage == null){
            createRegWindow();
        }
        regStage.show();
    }

    public void registration(String login, String password, String nickname){
        if (socket == null || socket.isClosed()){
            connect();
        }

        String msg = String.format("/reg %s %s %s", login, password, nickname);
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}