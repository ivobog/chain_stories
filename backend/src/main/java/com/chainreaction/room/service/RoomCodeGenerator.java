package com.chainreaction.room.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        char[] code = new char[6];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
