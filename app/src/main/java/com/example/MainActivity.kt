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

        // UI 우선 설정 (검은 화면 방지)
        enableEdgeToEdge()
        
        // Initialize Room Database & Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ComparisonRepository(database.comparisonDao())
        
        // Instantiate ViewModel using custom factory
        val viewModel: ComparisonViewModel by viewModels {
            ComparisonViewModelFactory(repository, application)
        }

        // Initialize GDPR/CCPA Consent Flow
        // 광고 초기화 로직 분리 (메인 스레드 블로킹 방지)
        com.example.util.ConsentManager.gatherConsent(this, object : com.example.util.ConsentManager.OnConsentCheckCompleteListener {
            override fun onConsentCheckComplete() {
                if (com.example.util.ConsentManager.canRequestAds()) {
                    com.google.android.gms.ads.MobileAds.initialize(this@MainActivity)
                }
            }
        })

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
