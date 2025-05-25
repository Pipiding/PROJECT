package com.myfinanceapp.auth;

/**
 * 安全问题模型
 * 对应 security_questions.json 中的每个元素
 */
public class SecurityQuestion {
    private String question;
    private String hashedAnswer; // 答案的哈希值

    // For deserialization by Gson
    public SecurityQuestion() {}

    // For creating new questions before hashing (e.g., during registration input)
    public SecurityQuestion(String question, String answer) {
        this.question = question;
        // The answer provided here is plaintext, it will be hashed by AuthService.register
        this.hashedAnswer = answer; // Temporarily store plaintext, will be overwritten with hash
    }

    // For creating questions with hashed answers (e.g., after loading from file)
    public SecurityQuestion(String question, String hashedAnswer, boolean isHashed) {
        this.question = question;
        this.hashedAnswer = hashedAnswer;
    }


    // Getters

    public String getQuestion() { return question; }
    public String getHashedAnswer() { return hashedAnswer; }

    // Setter for hashedAnswer (if needed, e.g., for initial setup)
    public void setHashedAnswer(String hashedAnswer) { this.hashedAnswer = hashedAnswer; }

    // Getter for answer (NOT recommended in production code after hashing!)
    // For demonstration/initial setup purposes only, do not use for verification
    // public String getAnswer() { return null; } // Never expose raw answer after hashing!

    @Override
    public String toString() {
        return "SecurityQuestion{" +
                "question='" + question + '\'' +
                ", hashedAnswer='[HASHED]'" + // Avoid logging sensitive info
                '}';
    }
}
    