package com.example.service;

import com.example.model.User;
import com.example.repository.UserRepository;

public class UserService implements Auditable {

    private UserRepository userRepository;

    public User findUser(long id) {
        return userRepository.findById(id);
    }

    public void registerUser(User user) {
        userRepository.save(user);
    }

    @Override
    public void audit() {
    }
}