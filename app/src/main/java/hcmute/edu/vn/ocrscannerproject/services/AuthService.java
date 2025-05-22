package hcmute.edu.vn.ocrscannerproject.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import hcmute.edu.vn.ocrscannerproject.core.entities.User;

/**
 * Service for handling authentication operations with Firebase.
 */
public class AuthService {
    private static final String TAG = "AuthService";
    
    private final FirebaseAuth auth;
    
    /**
     * Initializes the authentication service.
     */
    public AuthService() {
        auth = FirebaseAuth.getInstance();
    }
    
    /**
     * Checks if a user is currently signed in.
     * 
     * @return True if a user is signed in, false otherwise
     */
    public boolean isUserSignedIn() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Gets the current user.
     * 
     * @return The current user, or null if not signed in
     */
    public User getCurrentUser() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            return new User(
                    firebaseUser.getUid(),
                    firebaseUser.getEmail(),
                    firebaseUser.getDisplayName(),
                    firebaseUser.isAnonymous()
            );
        }
        return null;
    }
    
    /**
     * Signs in a user with email and password.
     * 
     * @param email The user's email
     * @param password The user's password
     * @param callback The callback to be invoked when the sign-in operation completes
     */
    public void signInWithEmailAndPassword(String email, String password,
                                         final AuthCallback<User> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                User user = new User(
                                        firebaseUser.getUid(),
                                        firebaseUser.getEmail(),
                                        firebaseUser.getDisplayName(),
                                        firebaseUser.isAnonymous()
                                );
                                callback.onSuccess(user);
                            } else {
                                callback.onError(new IllegalStateException("User is null after successful sign-in"));
                            }
                        } else {
                            Log.e(TAG, "Error signing in with email and password", task.getException());
                            callback.onError(task.getException());
                        }
                    }
                });
    }
    
    /**
     * Signs in a user with a Google ID token.
     * 
     * @param idToken The Google ID token
     * @param callback The callback to be invoked when the sign-in operation completes
     */
    public void signInWithGoogle(String idToken, final AuthCallback<User> callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                User user = new User(
                                        firebaseUser.getUid(),
                                        firebaseUser.getEmail(),
                                        firebaseUser.getDisplayName(),
                                        firebaseUser.isAnonymous()
                                );
                                callback.onSuccess(user);
                            } else {
                                callback.onError(new IllegalStateException("User is null after successful sign-in"));
                            }
                        } else {
                            Log.e(TAG, "Error signing in with Google", task.getException());
                            callback.onError(task.getException());
                        }
                    }
                });
    }
    
    /**
     * Signs in anonymously.
     * 
     * @param callback The callback to be invoked when the sign-in operation completes
     */
    public void signInAnonymously(final AuthCallback<User> callback) {
        auth.signInAnonymously()
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                User user = new User(
                                        firebaseUser.getUid(),
                                        firebaseUser.getEmail(),
                                        firebaseUser.getDisplayName(),
                                        firebaseUser.isAnonymous()
                                );
                                callback.onSuccess(user);
                            } else {
                                callback.onError(new IllegalStateException("User is null after successful sign-in"));
                            }
                        } else {
                            Log.e(TAG, "Error signing in anonymously", task.getException());
                            callback.onError(task.getException());
                        }
                    }
                });
    }
    
    /**
     * Creates a new user with email and password.
     * 
     * @param email The user's email
     * @param password The user's password
     * @param displayName The user's display name
     * @param callback The callback to be invoked when the create operation completes
     */
    public void createUserWithEmailAndPassword(String email, String password, final String displayName,
                                             final AuthCallback<User> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                // Set the display name
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(displayName)
                                        .build();
                                
                                firebaseUser.updateProfile(profileUpdates)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    User user = new User(
                                                            firebaseUser.getUid(),
                                                            firebaseUser.getEmail(),
                                                            displayName,
                                                            firebaseUser.isAnonymous()
                                                    );
                                                    callback.onSuccess(user);
                                                } else {
                                                    Log.e(TAG, "Error updating user profile", task.getException());
                                                    callback.onError(task.getException());
                                                }
                                            }
                                        });
                            } else {
                                callback.onError(new IllegalStateException("User is null after successful sign-up"));
                            }
                        } else {
                            Log.e(TAG, "Error creating user with email and password", task.getException());
                            callback.onError(task.getException());
                        }
                    }
                });
    }
    
    /**
     * Signs out the current user.
     */
    public void signOut() {
        auth.signOut();
    }
    
    /**
     * Sends a password reset email to the specified email address.
     * 
     * @param email The email address
     * @param callback The callback to be invoked when the operation completes
     */
    public void sendPasswordResetEmail(String email, final AuthCallback<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            Log.e(TAG, "Error sending password reset email", task.getException());
                            callback.onError(task.getException());
                        }
                    }
                });
    }
    
    /**
     * Updates the current user's display name.
     * 
     * @param displayName The new display name
     * @param callback The callback to be invoked when the operation completes
     */
    public void updateDisplayName(String displayName, final AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build();
            
            firebaseUser.updateProfile(profileUpdates)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                callback.onSuccess(null);
                            } else {
                                Log.e(TAG, "Error updating display name", task.getException());
                                callback.onError(task.getException());
                            }
                        }
                    });
        } else {
            callback.onError(new IllegalStateException("User is not signed in"));
        }
    }
    
    /**
     * Updates the current user's email address.
     * 
     * @param email The new email address
     * @param callback The callback to be invoked when the operation completes
     */
    public void updateEmail(String email, final AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            firebaseUser.updateEmail(email)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                callback.onSuccess(null);
                            } else {
                                Log.e(TAG, "Error updating email", task.getException());
                                callback.onError(task.getException());
                            }
                        }
                    });
        } else {
            callback.onError(new IllegalStateException("User is not signed in"));
        }
    }
    
    /**
     * Updates the current user's password.
     * 
     * @param newPassword The new password
     * @param callback The callback to be invoked when the operation completes
     */
    public void updatePassword(String newPassword, final AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            firebaseUser.updatePassword(newPassword)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                callback.onSuccess(null);
                            } else {
                                Log.e(TAG, "Error updating password", task.getException());
                                callback.onError(task.getException());
                            }
                        }
                    });
        } else {
            callback.onError(new IllegalStateException("User is not signed in"));
        }
    }
    
    /**
     * Links an anonymous user with an email and password.
     * 
     * @param email The email address
     * @param password The password
     * @param callback The callback to be invoked when the operation completes
     */
    public void linkAnonymousUserWithEmailAndPassword(String email, String password,
                                                    final AuthCallback<User> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null && firebaseUser.isAnonymous()) {
            AuthCredential credential = EmailAuthProvider.getCredential(email, password);
            
            firebaseUser.linkWithCredential(credential)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                FirebaseUser linkedUser = task.getResult().getUser();
                                if (linkedUser != null) {
                                    User user = new User(
                                            linkedUser.getUid(),
                                            linkedUser.getEmail(),
                                            linkedUser.getDisplayName(),
                                            linkedUser.isAnonymous()
                                    );
                                    callback.onSuccess(user);
                                } else {
                                    callback.onError(new IllegalStateException("User is null after successful linking"));
                                }
                            } else {
                                Log.e(TAG, "Error linking anonymous user", task.getException());
                                callback.onError(task.getException());
                            }
                        }
                    });
        } else {
            callback.onError(new IllegalStateException("User is not signed in or is not anonymous"));
        }
    }
    
    /**
     * Callback interface for authentication operations.
     * 
     * @param <T> The type of the result
     */
    public interface AuthCallback<T> {
        /**
         * Called when the operation is successful.
         * 
         * @param result The result of the operation
         */
        void onSuccess(T result);
        
        /**
         * Called when the operation fails.
         * 
         * @param e The exception that caused the failure
         */
        void onError(Exception e);
    }
} 