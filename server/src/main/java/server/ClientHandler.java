package server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.ArrayList;

public class ClientHandler {

    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nickName;
    private String login;
    private String password;
    private static final Logger logger = LogManager.getLogger("chatLogger");
    private static final Logger loggerError = LogManager.getLogger("chatError");

 public ClientHandler(Server server, Socket socket){

     try {
         this.socket = socket;
         this.server = server;

         in = new DataInputStream(socket.getInputStream());
         out = new DataOutputStream(socket.getOutputStream());

   //      new Thread(() -> {
             server.getExecutorService().execute(() -> {
             try {
                 // цикл аутентификации
                 while (true) {
                     String str = in.readUTF();
                     if (str.equals("/end")) {
                         out.writeUTF("/end");
                         logger.info("Client disconnect. nick: " + nickName);
                         throw (new RuntimeException("Клиент отключился"));
                     }

                     if (str.startsWith("/auth")) {
                         String[] token = str.split("\\s");
                         if (token.length < 3) {
                             continue;
                         }

                         //String newNick = server.getAuthService().getNicknameByLoginAndPassword(token[1], token[2]);
                         // Авторизация пользователя через БД
                         String newNick = server.getNicknameByLoginAndPasswordDB(token[1], token[2]);

                         if (newNick != null) {
                             login = token[1];
                             if (!server.isLoginAuthenticated(login)) {
                                 nickName = newNick;
                                 sendMsg("/auth_ok " + nickName);

                                 server.subscribe(this);

                                 logger.info("Client authenticated. nick " + nickName
                                         + " Address: " + socket.getRemoteSocketAddress());
                                 break;
                             } else {
                                 sendMsg("This login already used");
                             }
                         } else {
                             sendMsg("wrong login or password");
                             logger.info("Client wrote wrong login or password");
                         }
                     }

                     //Registration
                     if (str.startsWith("/reg")) {
                         String[] token = str.split("\\s", 4);
                         if (token.length < 4) {
                             continue;
                         }
                   //      boolean b = server.getAuthService().registration(token[1], token[2], token[3]);
                         //Регистрация пользователя в БД
                         boolean b = server.getRegistration(token[1], token[2], token[3]);
                         if (b) {
                             sendMsg("/reg_ok");
                             logger.info("Client reg with nick: " + token[3]);
                         } else {
                             sendMsg("/reg_no");
                         }
                     }
                 }

                 // цикл работы
                 while (true) {

                     String str = in.readUTF();
                     if (str.startsWith("/")) {
                         if (str.equals("/end")) {
                             out.writeUTF("/end");
                             logger.info("Client disconnect. nick: " + nickName);
                             break;
                         }
                         if (str.startsWith("/change")) {
                             String[] nick = str.split("\\s+", 2);
                             System.out.println(nick[1]);
                             String login = this.getLogin();
                             String password = this.getPassword();
                             server.unsubscribe(this);
                             String newNick = server.changeNick(nick[1], this);
                             System.out.println(newNick);

                             if (newNick != null) {

                                 sendMsg("/auth_ok " + newNick);
                                 server.subscribe(this);
                             break;}
                             else {
                                 sendMsg("This login already used");
                             }
                         }

                         if (str.startsWith("/w")) {
                             String[] personMsg = str.split("\\s+", 3);
                             server.personalMsg(this, personMsg[1], personMsg[2]);
                         }
                     } else {
                         server.broadcastMsg(this, str);
                     }
                 }
             }catch (RuntimeException e) {
                 loggerError.error("Error creation thread by ExecutorService", e);
             } catch (IOException e) {
                 e.printStackTrace();
             } finally {
                 server.unsubscribe(this);
     //            logger.info(" Client disconnect " + socket.getRemoteSocketAddress());
                 try {
                     socket.close();
                 } catch (IOException e) {
                     loggerError.error("Error close socket", e);
                 }
             }
         });

     } catch (IOException e) {
         loggerError.error("Error creation new ClientHandler", e);
     }
 }

    public void sendMsg (String msg) {
     try {
         out.writeUTF(msg);
     } catch (IOException e) {
         loggerError.error("Error send message", e);
     }
 }

    public String getNickName() {
        return nickName;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword(){return password;}
}
