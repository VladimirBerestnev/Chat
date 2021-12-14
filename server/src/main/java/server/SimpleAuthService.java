package server;

import java.util.ArrayList;
import java.util.List;

public class SimpleAuthService implements AuthService {

    private class userdata{
        String login;
        String password;
        String nickname;

        public userdata(String login, String password, String nickname){
            this.login = login;
            this.password = password;
            this.nickname = nickname;
        }
    }

    private List<userdata> users;

    public SimpleAuthService(){
        users = new ArrayList<>();
        users.add(new userdata("user1", "user1", "user1"));
        users.add(new userdata("user2", "user2", "user2"));
        users.add(new userdata("user3", "user3", "user3"));
    }

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        for (userdata u : users) {
            if (u.login.equals(login) && u.password.equals(password))
                return u.nickname;
        }
        return null;
    }

    @Override
    public boolean registration(String login, String password, String nickname) {
        for (userdata u : users) {
            if (u.login.equals(login) && u.nickname.equals(nickname))
                return false;
        }
        users.add(new userdata(login, password, nickname));
        return true;
    }
}
