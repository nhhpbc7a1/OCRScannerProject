package hcmute.edu.vn.ocrscannerproject;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.data.SampleDataProvider;
import hcmute.edu.vn.ocrscannerproject.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabCamera;
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "savedInstanceState = " + savedInstanceState);
        // Hide default ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_main);

        try {
            // Set up Bottom Navigation
            bottomNav = findViewById(R.id.bottomNavigationView);
            
            // Set up FAB Camera button
            fabCamera = findViewById(R.id.fab_camera);
            fabCamera.setOnClickListener(view -> {
                navController.navigate(R.id.scanFragment);
            });
            
            // Set up Navigation Controller using NavHostFragment
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_nav_host);

            if (navHostFragment == null) {
                Log.e(TAG, "NavHostFragment not found! Check the ID in layout");
                return;
            }
            
            navController = navHostFragment.getNavController();
            
            // Set up manual navigation item selection instead of automatic setup
            bottomNav.setOnItemSelectedListener(this);
            
            // Set initial selected item
            bottomNav.setSelectedItemId(R.id.homeFragment);
            
            // Add destination change listener to handle bottom nav visibility
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destinationId = destination.getId();
                // Show bottom nav only in Home and Settings fragments
                if (destinationId == R.id.homeFragment || destinationId == R.id.settingsFragment) {
                    showBottomNav();
                } else {
                    // Hide in all other fragments (scan, review, extract text, etc.)
                    hideBottomNav();
                }
            });
            
            // Initialize sample data in background
            initializeSampleData();
            
            // Check if we should start with camera
            if (savedInstanceState == null) { // Only check on first creation
                if (SettingsFragment.shouldStartWithCamera(this)) {
                    // Navigate to camera fragment
                    navController.navigate(R.id.fab_camera);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Navigation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize sample data in a background thread
     */
    private void initializeSampleData() {
        executor.execute(() -> {
            try {
                SampleDataProvider sampleDataProvider = SampleDataProvider.getInstance(this);
                boolean created = sampleDataProvider.initializeSampleDataIfNeeded();
                
                if (created) {
                    runOnUiThread(() -> 
                        Toast.makeText(this, "Sample documents created", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing sample data: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        // Using if statements instead of switch for compatibility
        if (itemId == R.id.homeFragment) {
            navController.navigate(R.id.homeFragment);
            return true;
        } else if (itemId == R.id.settingsFragment) {
            navController.navigate(R.id.settingsFragment);
            return true;
        }
        
        return false;
    }

    public void showBottomNav() {
        if (bottomNav != null && fabCamera != null) {
            bottomNav.setVisibility(View.VISIBLE);
            fabCamera.setVisibility(View.VISIBLE);
        }
    }

    public void hideBottomNav() {
        if (bottomNav != null && fabCamera != null) {
            bottomNav.setVisibility(View.GONE);
            fabCamera.setVisibility(View.GONE);
        }
    }
}