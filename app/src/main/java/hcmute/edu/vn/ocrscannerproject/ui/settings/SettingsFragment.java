package hcmute.edu.vn.ocrscannerproject.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.User;
import hcmute.edu.vn.ocrscannerproject.services.AuthService;

/**
 * Fragment for displaying and managing user settings.
 */
public class SettingsFragment extends Fragment {
    
    private static final int RC_SIGN_IN = 9001;
    private static final String PREFS_NAME = "OCRScannerPrefs";
    private static final String KEY_START_WITH_CAMERA = "start_with_camera";
    
    private SettingsViewModel viewModel;
    private ImageView imgUserAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvStorageUsage;
    private ProgressBar progressStorage;
    private Button btnSignIn;
    private Button btnSignOut;
    private LinearLayout layoutSync;
    private SwitchCompat switchStartWithCamera;
    private LinearLayout layoutFileNameFormat;
    private TextView tvFileNameFormat;
    private TextView tvSaveLocation;
    private LinearLayout layoutFreeUpSpace;
    private LinearLayout layoutPermissions;
    
    // Google Sign-In client
    private GoogleSignInClient googleSignInClient;
    private AuthService authService;
    
    private SharedPreferences sharedPreferences;
    
    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        imgUserAvatar = view.findViewById(R.id.img_user_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        tvEmail = view.findViewById(R.id.tv_email);
        tvStorageUsage = view.findViewById(R.id.tv_storage_usage);
        progressStorage = view.findViewById(R.id.progress_storage);
        btnSignIn = view.findViewById(R.id.btn_sign_in);
        btnSignOut = view.findViewById(R.id.btn_sign_out);
        layoutSync = view.findViewById(R.id.layout_sync);
        
        // Settings components
        switchStartWithCamera = view.findViewById(R.id.switch_start_with_camera);
        layoutFileNameFormat = view.findViewById(R.id.layout_file_name_format);
        tvFileNameFormat = view.findViewById(R.id.tv_file_name_format);
        tvSaveLocation = view.findViewById(R.id.tv_save_location);
        layoutFreeUpSpace = view.findViewById(R.id.layout_free_up_space);
        layoutPermissions = view.findViewById(R.id.layout_permissions);
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        
        // Initialize auth service
        authService = new AuthService();
        
        // Configure Google Sign-In (basic profile only - no token)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
        
        // Load saved preference
        boolean startWithCamera = sharedPreferences.getBoolean(KEY_START_WITH_CAMERA, false);
        switchStartWithCamera.setChecked(startWithCamera);
        
        // Set up observers
        setupObservers();
        
        // Set up button listeners
        setupButtonListeners();
    }
    
    private void setupObservers() {
        // User profile observers
        viewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            tvUsername.setText(name);
            
            // Update UI based on whether user is signed in
            boolean isSignedIn = !name.equals("Guest User");
            btnSignIn.setVisibility(isSignedIn ? View.GONE : View.VISIBLE);
            btnSignOut.setVisibility(isSignedIn ? View.VISIBLE : View.GONE);
        });
        
        viewModel.getUserEmail().observe(getViewLifecycleOwner(), email -> {
            tvEmail.setText(email);
        });
        
        // Storage observers
        viewModel.getStorageUsage().observe(getViewLifecycleOwner(), usage -> {
            tvStorageUsage.setText(usage);
        });
        
        viewModel.getStoragePercentage().observe(getViewLifecycleOwner(), percentage -> {
            progressStorage.setProgress(percentage);
            // Change progress bar color based on usage
            if (percentage > 90) {
                progressStorage.setProgressTintList(requireContext().getColorStateList(android.R.color.holo_red_light));
            } else if (percentage > 75) {
                progressStorage.setProgressTintList(requireContext().getColorStateList(android.R.color.holo_orange_light));
            } else {
                progressStorage.setProgressTintList(requireContext().getColorStateList(android.R.color.holo_green_light));
            }
        });
        
        // Status observers
        viewModel.getIsSyncing().observe(getViewLifecycleOwner(), isSyncing -> {
            layoutSync.setEnabled(!isSyncing);
            // You could also change the icon or add a progress indicator when syncing
        });
        
        viewModel.getIsCleaningCache().observe(getViewLifecycleOwner(), isCleaning -> {
            layoutFreeUpSpace.setEnabled(!isCleaning);
        });
        
        // Toast message observer
        viewModel.getToastMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Scan settings
        viewModel.getStartWithCamera().observe(getViewLifecycleOwner(), startWithCamera -> {
            switchStartWithCamera.setChecked(startWithCamera);
        });
        
        // Document settings
        viewModel.getFileNameFormat().observe(getViewLifecycleOwner(), format -> {
            tvFileNameFormat.setText(format);
        });
        
        viewModel.getSaveLocation().observe(getViewLifecycleOwner(), location -> {
            tvSaveLocation.setText(location);
        });
    }
    
    private void setupButtonListeners() {
        // Sign in button
        btnSignIn.setOnClickListener(v -> {
            signIn();
        });
        
        // Sign out button
        btnSignOut.setOnClickListener(v -> {
            signOut();
        });
        
        // Sync button
        layoutSync.setOnClickListener(v -> {
            viewModel.syncData();
        });
        
        // Start with camera switch
        switchStartWithCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save the new setting
            sharedPreferences.edit().putBoolean(KEY_START_WITH_CAMERA, isChecked).apply();
            
            // Show confirmation message
            String message = isChecked ? "Camera will open on startup" : "Camera disabled on startup";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
        
        // File name format
        layoutFileNameFormat.setOnClickListener(v -> {
            showFileNameFormatDialog();
        });
        
        // Free up space
        layoutFreeUpSpace.setOnClickListener(v -> {
            showDataUsageDialog();
        });
        
        // Permissions
        layoutPermissions.setOnClickListener(v -> {
            showPermissionsMenu();
        });
    }
    
    private void showDataUsageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Storage Usage")
               .setMessage("App Storage: " + viewModel.getAppStorageSize() + "\nCache Size: " + viewModel.getCacheSize())
               .setPositiveButton("Clean Cache", (dialog, which) -> {
                   viewModel.cleanCache();
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void showFileNameFormatDialog() {
        // Options for file name format
        final String[] options = {
            "Scan_yyyyMMdd_HHmmss", 
            "OCR_yyyyMMdd_HHmmss",
            "Scan_dd-MM-yyyy_HH-mm",
            "Custom"
        };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select File Name Format")
               .setItems(options, (dialog, which) -> {
                   if (which == options.length - 1) {
                       // Custom option - show dialog with predefined components
                       showCustomFileNameDialog();
                   } else {
                       viewModel.setFileNameFormat(options[which]);
                   }
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void showCustomFileNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View customView = getLayoutInflater().inflate(R.layout.dialog_custom_filename, null);
        
        // Set up the dialog with checkboxes for date components
        // This is just a placeholder - you would implement the actual UI with checkboxes
        
        builder.setTitle("Customize File Name")
               .setView(customView)
               .setPositiveButton("Save", (dialog, which) -> {
                   // Get selected options and create format string
                   // For now, just use a predefined format
                   viewModel.setFileNameFormat("Custom_yyyyMMdd");
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void showPermissionsMenu() {
        // Get permission statuses
        boolean cameraPermission = viewModel.hasCameraPermission(requireContext());
        boolean storagePermission = viewModel.hasStoragePermission(requireContext());
        boolean microphonePermission = viewModel.hasMicrophonePermission(requireContext());
        
        final String[] permissions = {
            "Camera",
            "Storage",
            "Microphone"
        };
        
        final String[] statuses = {
            cameraPermission ? "Allowed" : "Set",
            storagePermission ? "Allowed" : "Set",
            microphonePermission ? "Allowed" : "Set"
        };
        
        // Create list items with permission name and status
        CharSequence[] items = new CharSequence[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            items[i] = permissions[i] + " (" + statuses[i] + ")";
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("App Permissions")
               .setItems(items, (dialog, which) -> {
                   openAppSettings();
               })
               .setPositiveButton("Open Settings", (dialog, which) -> {
                   openAppSettings();
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
    
    private void signIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    
    private void signOut() {
        // Sign out from Firebase
        viewModel.signOut();
        
        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
            // Update UI after sign out
            updateUIAfterSignOut();
        });
    }
    
    private void updateUIAfterSignOut() {
        btnSignIn.setVisibility(View.VISIBLE);
        btnSignOut.setVisibility(View.GONE);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful
                GoogleSignInAccount account = task.getResult(ApiException.class);
                // Since we're not using Firebase Auth with tokens, we'll just use the account info
                firebaseAuthWithGoogle(null);
            } catch (ApiException e) {
                // Google Sign In failed
                Toast.makeText(requireContext(), "Google sign in failed: " + e.getStatusCode(), 
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void firebaseAuthWithGoogle(String idToken) {
        // Since we're not requesting an idToken, we'll use email authentication instead
        // For simplicity, we'll just update the UI as if signed in
        viewModel.loadUserData();
        Toast.makeText(requireContext(), "Sign in successful", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when fragment becomes visible
        viewModel.loadUserData();
        viewModel.refreshStorageInfo();
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }
    
    // Method to check if start with camera is enabled
    public static boolean shouldStartWithCamera(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_START_WITH_CAMERA, false);
    }
} 