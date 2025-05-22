package hcmute.edu.vn.ocrscannerproject.core.entities;

/**
 * Represents a user in the application.
 * Users can be authenticated or anonymous.
 */
public class User {
    private String userId;
    private String email;
    private String displayName;
    private boolean isAnonymous;

    /**
     * Constructs a new User with the specified properties.
     * 
     * @param userId      The unique identifier for the user
     * @param email       The user's email address
     * @param displayName The user's display name
     * @param isAnonymous Whether the user is authenticated anonymously
     */
    public User(String userId, String email, String displayName, boolean isAnonymous) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.isAnonymous = isAnonymous;
    }

    /**
     * Gets the unique identifier for the user.
     * 
     * @return The user's ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the user's email address.
     * 
     * @return The user's email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Gets the user's display name.
     * 
     * @return The user's display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if the user is authenticated anonymously.
     * 
     * @return True if the user is anonymous, false otherwise
     */
    public boolean isAnonymous() {
        return isAnonymous;
    }
} 