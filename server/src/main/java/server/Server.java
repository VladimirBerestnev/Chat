package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {

    private static ServerSocket server;
    private static Socket socket;
    private static final int PORT = 60000;
    private List<ClientHandler> clients;
    private AuthService authService;
    private Connection connection;
    private ExecutorService executorService;
    private static final Logger logger = LogManager.getLogger("chatLogger");
    private static final Logger loggerError = LogManager.getLogger("chatError");

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Server() {
        executorService = Executors.newCachedThreadPool();
        clients = new CopyOnWriteArrayList<>();
        authService = new SimpleAuthService();

        try {
            server = new ServerSocket(PORT);
            //Соединение с БД
            connectDB();
          //  System.out.println("Started");
            logger.info("Server started");

            while (true) {

                socket = server.accept();
                logger.info("Client connect " + socket.getRemoteSocketAddress());
                new ClientHandler(this, socket);

                Thread t1 = new Thread(() -> {

                    try {
                        Scanner in2 = new Scanner(System.in);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                        while (true) {
                            String str = in2.nextLine();
                            broadcastMsgServer("Server : " + str);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                t1.start();
            }
        } catch (IOException | SQLException e) {
            loggerError.error("Error client connection", e);

        } finally {
            try {
                logger.info("Server closed");
                socket.close();
                server.close();
                //Закрыть соединение с БД
                disconnectDB();
            } catch (IOException | SQLException e) {
                loggerError.error("Error server or socket close", e);
            }
        }
    }

    public void broadcastMsgServer(String msg) {
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }

    public void broadcastMsg(ClientHandler sender, String msg) {
        try(BufferedWriter bw = new BufferedWriter(new FileWriter("chat1.txt", true))){

        String message = String.format("%s : %s", sender.getNickName(), msg);
        bw.write(message + "\n");
        for (ClientHandler c : clients) {
            c.sendMsg(message);
        }
        logger.info("User " + sender.getNickName() + " wrote message");
        } catch (IOException e){
            loggerError.error("File chat1.txt not found", e);
        }

    }

    public void reverseFile (ClientHandler clientHandler) throws IOException {
        File file = new File("chat1.txt");
        ArrayList<String> chatLines = new ArrayList<>();
       ReversedLinesFileReader reader = new ReversedLinesFileReader(file, Charset.defaultCharset());
            String line;

            int n_lines = 100;
            int counter = 0;

        while((line = reader.readLine()) != null && counter < n_lines)
        {
            chatLines.add(line);
            counter++;
        }

        for (int j = 1; j < chatLines.size() + 1; j++) {
            clientHandler.sendMsg(chatLines.get(chatLines.size() - j));
        }
        reader.close();

    }


    public void personalMsg(ClientHandler client, String nick, String msg) {

        String message = String.format("%s(private) : %s", client.getNickName(), msg);
        for (ClientHandler c : clients) {
            if (c.getNickName().equals(nick)) {
                c.sendMsg(message);
                if (client.equals(c)) {
                    return;
                }
                client.sendMsg(message);
                return;
            }
        }
        client.sendMsg("Пользователь не найден " + nick);
    }

    public void subscribe(ClientHandler clientHandler) throws IOException {
        clients.add(clientHandler);
        broadcastClientList();

       reverseFile(clientHandler);

    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastClientList();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public boolean isLoginAuthenticated(String login) {
        for (ClientHandler c : clients) {
            if (c.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    public void broadcastClientList() {
        StringBuilder sb = new StringBuilder("/clientList");
        for (ClientHandler c : clients) {
            sb.append(" ").append(c.getNickName());
        }
        String msg = sb.toString();
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }
    //Классы по работе с БД
    public void connectDB() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:chat.db");
    }

    public void disconnectDB() throws SQLException {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                loggerError.error("Error close connection DB", e);
                throw new RuntimeException(e);
            }
        }
    }

    public void selectByNick(String nick) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select * from users where nickname = ?")) {
            ps.setString(1, nick);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String nickname = rs.getString(1);
                String login = rs.getString(2);
                String password = rs.getString(3);

                System.out.printf("%s - %s - %s", nickname, login, password);
            }
        }
    }

    public String getNicknameByLoginAndPasswordDB(String login, String password) {
        try (PreparedStatement ps = connection.prepareStatement("select nickname from users where login = ? AND password = ?")) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            loggerError.error("Error get nickname", e);
        }
        return null;
    }

    public String checkNick(String nick) {
        try (PreparedStatement ps2 = connection.prepareStatement("select nickname from users where nickname = ?")) {
            ps2.setString(1, nick);
            ResultSet rs = ps2.executeQuery();

            while (rs.next()) {
                String nickname = rs.getString(1);
                System.out.println(nick);
                if (nickname != null) {
                    return null;
                }
            }
        } catch (SQLException e) {
            loggerError.error("Error check nickname", e);
        }
        return nick;
    }

    public String changeNick(String nick, ClientHandler client) {
        String nickname = checkNick(nick);
        String login = client.getLogin();
        if (nickname != null) {
            try (PreparedStatement ps3 = connection.prepareStatement("update users set nickname = ? where login = ?")) {
                ps3.setString(1, nickname);
                ps3.setString(2, login);
                ps3.executeUpdate();
                return nickname;
            } catch (SQLException e) {
                loggerError.error("Error change nick", e);
            }
        }
        return null;
    }

    public boolean getRegistration(String token1, String token2, String token3) {

        String nickname = checkNick(token3);
        if (nickname != null) {
            try (PreparedStatement ps1 = connection.prepareStatement("insert into users (nickname, login, password) values (?, ?, ?);")) {
                ps1.setString(1, nickname);
                ps1.setString(2, token1);
                ps1.setString(3, token2);
                ps1.executeUpdate();
                return true;

            } catch (SQLException e) {
                loggerError.error("Error registration new client", e);
            }
        }
        return false;
    }
}