package com.alzadash;

// Runs in a Shizuku (shell) process and injects touches and keys straight into a display.
interface IInput {
    void destroy() = 16777114;
    boolean touch(int display, int action, float x, float y, long downTime) = 1;
    boolean key(int display, int code) = 2;
    boolean moveTask(String pkg, int display) = 3;
}
