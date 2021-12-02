package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    private static ServerSocket server;
    private static Socket socket;
    private static final int PORT = 60000;
    private List<ClientHandler> clients;
    private AuthService authService;
    private Connection connection;

    public Server() {
        clients = new CopyOnWriteArrayList<>();
        authService = new SimpleAuthService();

        try {
            server = new ServerSocket(PORT);
            connectDB();
            System.out.println("Started");

            while (true) {

                socket = server.accept();
                System.out.println("Connect " + socket.getRemoteSocketAddress());
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
            e.printStackTrace();

        } finally {
            try {
                socket.close();
                server.close();
                disconnectDB();
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcastMsgServer(String msg) {
        for (ClientHandler c : clients) {
            c.sendMsg(msg);
        }
    }

    public void broadcastMsg(ClientHandler sender, String msg) {
        String message = String.format("%s : %s", sender.getNickName(), msg);
        for (ClientHandler c : clients) {
            c.sendMsg(message);
        }
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

    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        broadcastClientList();
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

    public void connectDB() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:chat.db");
    }

    public void disconnectDB() throws SQLException {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return nick;
    }

    public String changeNick(String nick, ClientHandler client) throws SQLException {
        String nickname = checkNick(nick);
        String login = client.getLogin();
        if (nickname != null) {
            try (PreparedStatement ps3 = connection.prepareStatement("update users set nickname = ? where login = ?")) {
                ps3.setString(1, nickname);
                ps3.setString(2, login);
                ps3.executeUpdate();
                return nickname;
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
                e.printStackTrace();
            }
        }
        return false;
    }
}