package com.example.Auth;

public class SignInResponse {
//    public String userId;
    public String token;

    public SignInResponse(String token) {
        this.token = token;
    }


    public String getToken() {
        return token;
    }
}