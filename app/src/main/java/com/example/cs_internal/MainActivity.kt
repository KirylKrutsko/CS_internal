package com.example.cs_internal

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import com.example.cs_internal.DataSingleton.adapters
import com.example.cs_internal.DataSingleton.filmLists
import com.example.cs_internal.DataSingleton.tags
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(){


    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var addButton : ImageButton
    private lateinit var user : FirebaseUser
    private lateinit var storageUserRef: StorageReference
    private lateinit var databaseUserRef : DatabaseReference
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestForPermissions()

        val authenticator = FirebaseAuth.getInstance()
        val userOrNull = authenticator.currentUser
        if(userOrNull == null){
            val intentToLoginActivity = Intent(this@MainActivity, LoginActivity::class.java)
            startActivity(intentToLoginActivity)
            finish()
        }
        if(!userOrNull!!.isEmailVerified) {
            val intent = Intent(this, EmailVerificationActivity::class.java)
            startActivity(intent)
            finish()
        }
        user = userOrNull

        val firebaseInstance = FirebaseDatabase.getInstance()
        firebaseInstance.goOffline()
        firebaseInstance.goOnline()
        databaseUserRef = firebaseInstance.reference.child("users/${user.uid}")
        storageUserRef = Firebase.storage.reference.child("users/${user.uid}")

        toolbar = findViewById(R.id.toolbar)
        toolbar.title = user.displayName
        ImageLoader().loadProfileImage(user.uid, storageUserRef, toolbar, this)
        setSupportActionBar(toolbar)

        val viewPager : ViewPager = findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = 2
        val pagerAdapter = PagerAdapter(supportFragmentManager, databaseUserRef, storageUserRef,this)
        pagerAdapter.setAdapters()
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(1, false)
        val tabLayout : TabLayout = findViewById(R.id.tabLayout)
        tabLayout.setupWithViewPager(viewPager)

        tabLayout.getTabAt(0)?.setIcon(R.drawable.outline_watch_later_24)
        tabLayout.getTabAt(1)?.setIcon(R.drawable.outline_remove_red_eye_24)
        tabLayout.getTabAt(2)?.setIcon(R.drawable.baseline_done_all_24)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setDistanceToTriggerSync(800)
        updateFilms(databaseUserRef)
        swipeRefresh.setOnRefreshListener {
                updateFilms(databaseUserRef)
        }

        addButton = findViewById(R.id.add_button)
        addButton.setOnClickListener(){
            val intent = Intent(this@MainActivity, FilmActivity::class.java)
            intent.apply {
                putExtra("new", true)
                putExtra("pageNum", viewPager.currentItem)
            }
            startActivity(intent)
        }

        toolbar.setNavigationOnClickListener {
            val intent = Intent(this@MainActivity, ProfileActivity::class.java)
            startActivity(intent)
        }

    }

    private fun requestForPermissions() {
        val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissions.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            ))
        } else {
            requestPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun updateFilms(databaseUserRef : DatabaseReference) {
        swipeRefresh.isRefreshing = true
        for(i in 0 until 3) {
            val size = filmLists[i].size
            filmLists[i].clear()
            adapters[i].notifyItemRangeRemoved(0, size)
        }
        val toRetrieveRef = databaseUserRef.orderByChild("lastEditTime")
        toRetrieveRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (filmSnapshot in dataSnapshot.children) {
                    val ref = filmSnapshot.key
                    if(ref == null){
                        Toast.makeText(this@MainActivity, "Failed to download some data.\nKey is null", Toast.LENGTH_LONG).show()
                    } else {
                        val editTime = filmSnapshot.child("lastEditTime").getValue(Long::class.java) ?: 0
                        if(editTime > 0){
                            val title = filmSnapshot.child("title").getValue(String::class.java) ?: ""
                            val type = filmSnapshot.child("type").getValue(Int::class.java) ?: 0
                            val desc = filmSnapshot.child("desc").getValue(String::class.java)
                            val rating = filmSnapshot.child("rating").getValue(String::class.java)
                            val imageLink = filmSnapshot.child("imageLink").getValue(String::class.java)
                            val mark = filmSnapshot.child("mark").getValue(Int::class.java)
                            val commentary = filmSnapshot.child("commentary").getValue(String::class.java)
                            val link = filmSnapshot.child("link").getValue(String::class.java)
                            val year = filmSnapshot.child("year").getValue(Int::class.java)
                            val watchTime = filmSnapshot.child("currentWatchTime").getValue(Int::class.java) ?: 0
                            val isASeries = filmSnapshot.child("isASeries").getValue(Boolean::class.java) ?: false
                            val filmTags = mutableListOf<String>()
                            val comments = mutableListOf<Comment>()
                            filmSnapshot.child("tags").children.forEach { tagSnapshot->
                                val tag = tagSnapshot.getValue(String::class.java) ?: ""
                                filmTags.add(tag)
                                if(!tags.contains(tag)) tags.add(tag)
                            }
                            filmSnapshot.child("comments").children.forEach { commentSnapshot->
                                val timecode = commentSnapshot.key?.toInt() ?: 0
                                val text = commentSnapshot.getValue(String::class.java) ?: ""
                                comments.add(Comment(timecode, text))
                            }
                            val newFilm = FilmItem(title, type, ref, desc, rating, imageLink, mark, commentary, link, year, watchTime, editTime, filmTags, comments, isASeries)
                            filmLists[type].add(0, newFilm)
                            adapters[type].notifyItemInserted(0)
                        }
                        else{
                            databaseUserRef.child(ref).removeValue()
                        }
                    }
                }
                swipeRefresh.isRefreshing = false
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, "Database connection error", Toast.LENGTH_SHORT).show()
                Toast.makeText(this@MainActivity, databaseError.message, Toast.LENGTH_LONG).show()
                swipeRefresh.isRefreshing = false
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchItem.setOnMenuItemClickListener {
            val intent = Intent(this@MainActivity, SearchActivity::class.java)
            startActivity(intent)
            false
        }

        val settingsButton = menu.findItem(R.id.settings)
        settingsButton.setOnMenuItemClickListener {
            val intent = Intent(this@MainActivity, ProfileActivity::class.java)
            startActivity(intent)
            false
        }

        return true
    }

}

