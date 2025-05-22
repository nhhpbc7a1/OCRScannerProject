package hcmute.edu.vn.ocrscannerproject;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.data.SampleDataProvider;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabCamera;
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Set up Toolbar as ActionBar
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            
            // Set up Bottom Navigation
            bottomNav = findViewById(R.id.view_bottom_navigation);
            
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
            
            // Configure the top level destinations (to not show back button on these screens)
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.homeFragment, R.id.settingsFragment
            ).build();
            
            // Connect Navigation Controller with the ActionBar
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            
            // Set up manual navigation item selection instead of automatic setup
            bottomNav.setOnItemSelectedListener(this);
            
            // Set initial selected item
            bottomNav.setSelectedItemId(R.id.homeFragment);
            
            // Initialize sample data in background
            initializeSampleData();
            
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

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return navController.navigateUp() || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}