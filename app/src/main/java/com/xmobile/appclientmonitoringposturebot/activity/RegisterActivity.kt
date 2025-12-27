package com.xmobile.appclientmonitoringposturebot.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityRegisterBinding
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.ValidateUtil
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.toString

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    private fun initControl() {
        binding.btnLogIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        listOf(binding.edtEmail, binding.edtPassword, binding.edtConfirmPassword, binding.edtUserName)
            .forEach { editText ->
                editText.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.txtWarning.visibility = View.GONE
                }
            }

        binding.btnRegister.setOnClickListener {
            val email = binding.edtEmail.text.toString()
            val password = binding.edtPassword.text.toString()
            val confirmPassword = binding.edtConfirmPassword.text.toString()
            val userName = binding.edtUserName.text.toString()
            if (validate(email, password, confirmPassword, userName)) register(email, password, userName)
        }
    }

    private fun register(email: String, password: String, userName: String) {
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                SupabaseProvider.client.from("user_profile")
                    .update(
                        buildJsonObject {
                            put("user_name", userName)
                        }
                    ) {
                        filter {
                            SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id?.let { eq("id", it) }
                        }
                    }

                binding.txtWarning.visibility = View.GONE
                Toast.makeText(
                    this@RegisterActivity,
                    "Đăng ký thành công",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("Register", e.message ?: "Unknown error")
                binding.txtWarning.visibility = View.VISIBLE
                binding.txtWarning.text = e.message ?: "Đăng ký thất bại"
            }
        }
    }

    private fun validate(email: String, password: String, confirmPassword: String, userName: String): Boolean {
        if (!ValidateUtil.emptyCheckEmail(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập email"
            return false
        }else if (!ValidateUtil.emptyCheckPassword(password)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập mật khẩu"
            return false
        }else if (!ValidateUtil.emptyCheckUserName(userName)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập tên người dùng"
            return false
        }else if (!ValidateUtil.formatCheck(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Email không đúng định dạng"
            return false
        }else if (!ValidateUtil.conditionCheckPassword(password)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Mật khẩu phải có ít nhất 6 ký tự"
            return false
        }else if (!ValidateUtil.conditionCheckMatching(password, confirmPassword)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Mật khẩu không khớp"
            return false
        }else if (!ValidateUtil.conditionCheckUserName(userName)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Tên người dùng phải có ít nhất 6 ký tự"
            return false
        }
        return true
    }
}