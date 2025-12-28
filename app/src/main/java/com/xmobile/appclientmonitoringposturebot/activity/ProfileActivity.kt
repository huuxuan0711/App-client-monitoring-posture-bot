package com.xmobile.appclientmonitoringposturebot.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityProfileBinding
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.ValidateUtil
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initControl() {
        val userName = getSharedPreferences("MyPrefs", MODE_PRIVATE).getString("user_name", "")
        binding.userName.hint = userName

        binding.imgBack.setOnClickListener {
            val intentNav = Intent(this, MainActivity::class.java)
            val device = intent.getSerializableExtra("device", UserDevice::class.java)
            intentNav.putExtra("device", device)
            startActivity(intentNav)
            finish()
        }

        binding.imgModifyUser.setOnClickListener {
            binding.txtDoneUser.visibility = View.VISIBLE
            binding.imgModifyUser.visibility = View.GONE
            binding.userName.isFocusableInTouchMode = true
            binding.userName.requestFocus()
        }

        binding.txtDoneUser.setOnClickListener {
            val userName = binding.userName.text.toString()
            if (validate(userName)) {
                binding.txtDoneUser.visibility = View.GONE
                binding.imgModifyUser.visibility = View.VISIBLE
                binding.userName.isFocusableInTouchMode = false
                binding.userName.clearFocus()
                binding.userName.hint = userName
                updateUserName(userName)
                val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                sharedPreferences.edit {
                    putString("user_name", userName)
                }
            }
        }

        binding.imgModifyGoal.setOnClickListener {
            binding.imgModifyGoal.visibility = View.GONE
            binding.imgDoneGoal.visibility = View.VISIBLE
            binding.edtGoal.isFocusableInTouchMode = true
            binding.edtGoal.requestFocus()
        }

        binding.imgDoneGoal.setOnClickListener {
            binding.imgModifyGoal.visibility = View.VISIBLE
            binding.imgDoneGoal.visibility = View.GONE
            binding.edtGoal.isFocusableInTouchMode = false
            binding.edtGoal.clearFocus()

            var goal: Int = 0

            if (!binding.edtGoal.text.toString().isEmpty()) {
                goal = binding.edtGoal.text.toString().toInt()
                if (!ValidateUtil.conditionCheckGoal(goal)){
                    Toast.makeText(this, "Mục tiêu hằng ngày phải nhỏ hơn 24 giờ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            val goalMs: Long = (goal * 60 * 60 * 1000).toLong()
            val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
            sharedPreferences.edit {
                putLong("goal_ms", goalMs)
            }
        }

        binding.layoutDeviceConnect.setOnClickListener {
            startActivity(Intent(this, PairDeviceActivity::class.java))
        }

        binding.txtChangePwd.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        binding.txtLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.auth.signOut()

                    val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                    sharedPreferences.edit {
                        clear()
                    }

                    val intent = Intent(this@ProfileActivity, StartActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)

                } catch (e: Exception) {
                    Log.e("Logout", e.message ?: "Unknown error")
                    Toast.makeText(
                        this@ProfileActivity,
                        "Đăng xuất thất bại",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.txtPolicy.setOnClickListener {
            Toast.makeText(this, "Tính năng này đang được phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUserName(newUserName: String) {
        lifecycleScope.launch {
            try {
                SupabaseProvider.client
                    .from("user_profile")
                    .update(
                        mapOf("user_name" to newUserName)
                    ) {
                        filter {
                            SupabaseProvider.client.auth.currentUserOrNull()?.id?.let { eq("id", it) }
                        }
                    }
            } catch (e: Exception) {
                Log.e("UpdateUserName", e.message ?: "Unknown error")
                throw e
            }
        }
    }

    private fun validate(userName: String): Boolean {
        if (!ValidateUtil.emptyCheckUserName(userName)) {
            Toast.makeText(this, "Vui lòng nhập tên người dùng", Toast.LENGTH_SHORT).show()
            return false
        } else if (!ValidateUtil.conditionCheckUserName(userName)) {
            Toast.makeText(this, "Tên người dùng phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}