package com.example.authdemo.service;

/**
 * Outcome of an authentication attempt.
 */
public enum LoginResult {
    SUCCESS,
    USERNAME_NOT_FOUND,
    WRONG_PASSWORD
}
