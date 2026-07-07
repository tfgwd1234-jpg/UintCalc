package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.ComparisonRepository
import com.example.ui.screens.ComparisonScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ComparisonViewModel
import com.example.ui.viewmodel.ComparisonViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room Database & Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ComparisonRepository(database.comparisonDao())
        
        // Instantiate ViewModel using custom factory
        val viewModel: ComparisonViewModel by viewModels {
            ComparisonViewModelFactory(repository, application)
        }

        // Initialize GDPR/CCPA Consent Flow
        com.example.util.ConsentManager.gatherConsent(this, object : com.example.util.ConsentManager.OnConsentCheckCompleteListener {
            override fun onConsentCheckComplete() {
                // Initialize Google Mobile Ads SDK
                Thread {
                    com.google.android.gms.ads.MobileAds.initialize(this@MainActivity)
                }.start()
            }
        })

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ComparisonScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
