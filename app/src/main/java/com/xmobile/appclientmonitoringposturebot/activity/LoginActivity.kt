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
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email

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
            startActivity(Intent(this, ResetPasswordActivity::class.java))
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
                val user = SupabaseProvider.client.auth.currentSessionOrNull()?.user
                val userProfile = getUserProfile(user?.id)
                val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                sharedPreferences.edit {
                    putString("user_id", user?.id)
                    putString("user_name", userProfile?.user_name)
                }

                val intent = Intent(this@LoginActivity, PairDeviceActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                binding.txtWarning.visibility = View.VISIBLE
                binding.txtWarning.text = e.message
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