package com.xmobile.appclientmonitoringposturebot.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityLoginBinding
import com.xmobile.appclientmonitoringposturebot.model.UserProfile
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.ValidateUtil
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlin.toString
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isVisiblePassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    private fun initControl() {
        if (intent.hasExtra("email")) {
            binding.edtEmail.setText(intent.getStringExtra("email"))
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.txtForgetPassword.setOnClickListener {
            startActivity(Intent(this, ForgetPasswordActivity::class.java))
        }

        binding.imgShow.setOnClickListener {
            isVisiblePassword = !isVisiblePassword
            binding.edtPassword.inputType = if (isVisiblePassword) {
                binding.imgShow.setImageResource(R.drawable.ic_show)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.imgShow.setImageResource(R.drawable.ic_hide)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            // Move cursor to end
            binding.edtPassword.setSelection(binding.edtPassword.text?.length ?: 0)
        }

        binding.edtEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtWarning.visibility = View.GONE
        }
        binding.edtPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtWarning.visibility = View.GONE
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString()
            val password = binding.edtPassword.text.toString()
            if (validate(email, password)) login(email, password)
        }
    }

    private fun login(email: String, password: String) {
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val session = SupabaseProvider.client.auth.currentSessionOrNull()
                    ?: throw IllegalStateException("Session not ready")

                val user = session.user
                Log.d("AUTH", "Login success, userId=${user?.id}")

                val userProfile = getUserProfile(user?.id)
                getSharedPreferences("MyPrefs", MODE_PRIVATE).edit {
                    putString("user_id", user?.id)
                    putString("user_name", userProfile?.user_name)
                }

                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        Log.d("FCM", "Got FCM token: $token")
                        saveFcmToken(token)
                    }
                    .addOnFailureListener {
                        Log.e("FCM", "Failed to get FCM token", it)
                    }

                startActivity(Intent(this@LoginActivity, PairDeviceActivity::class.java))
                finish()

            } catch (e: Exception) {
                Log.e("AUTH", "Login failed", e)
                binding.txtWarning.visibility = View.VISIBLE
                binding.txtWarning.text = e.message
            }
        }
    }

    private fun saveFcmToken(token: String) {
        Log.d("FCM", "saveFcmToken() called")

        val supabase = SupabaseProvider.client
        val user = supabase.auth.currentUserOrNull()

        if (user == null) {
            Log.w("FCM", "User is null, skip saving FCM token")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.from("user_fcm_tokens").upsert(
                    buildJsonObject {
                        put("user_id", user.id)
                        put("fcm_token", token)
                        put("is_active", true)
                    },
                    onConflict = "fcm_token"
                )

                Log.d("FCM", "FCM token saved to Supabase")

            } catch (e: Exception) {
                Log.e("FCM", "Failed to save FCM token", e)
            }
        }
    }

    private suspend fun getUserProfile(id: String?): UserProfile? {
        return SupabaseProvider.client
            .from("user_profile")
            .select {
                filter {
                    id?.let { eq("id", it) }
                }
            }
            .decodeSingle<UserProfile>()
    }

    private fun validate(email: String, password: String): Boolean {
        if (!ValidateUtil.emptyCheckEmail(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập email"
            return false
        }else if (!ValidateUtil.emptyCheckPassword(password)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập mật khẩu"
            return false
        }else if (!ValidateUtil.formatCheck(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Email không đúng định dạng"
            return false
        }
        return true
    }
}