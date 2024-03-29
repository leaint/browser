package com.example.clock.ui.main

import android.app.AlertDialog
import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import com.example.clock.R
import com.example.clock.SettingsActivity
import java.io.File


private const val ARG_FILENAME = "filename"

/**
 * A simple [Fragment] subclass.
 * Use the [EditFileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class EditFileFragment : Fragment() {

    private var filename: String? = null
    private var changed = false
    private var inited = false
    private lateinit var editText: EditText

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (changed) {
                val oo = this
                AlertDialog.Builder(context).apply {
                    setTitle("文本内容已改变。要退出吗？")
                    setPositiveButton(android.R.string.cancel, null)
                    setNegativeButton(android.R.string.yes) { dialog, which ->
                        run {
                            oo.isEnabled = false
                            activity.onBackPressed()
                        }
                    }
                }.show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filename = it.getString(ARG_FILENAME)
        }
        setHasOptionsMenu(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.isEnabled = false
        onBackPressedCallback.remove()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        editText = inflater.inflate(R.layout.fragment_edit_file, container, false) as EditText

        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        onBackPressedCallback.isEnabled = changed

        editText.addTextChangedListener {
            if (inited) {
                changed = true
                onBackPressedCallback.isEnabled = changed
                activity?.title = "Edit `$filename` *"
            }
        }

        return editText
    }

    @Deprecated("Deprecated in Java")
    override fun onResume() {
        super.onResume()
        activity?.title = "Edit `$filename`"
    }

    @Deprecated("Deprecated in Java")
    override fun onStart() {
        super.onStart()
        val ctx = context
        val fname = filename
        if (ctx == null || fname == null) {
            return
        }
        val f = File(
            ctx.getExternalFilesDir(null),
            fname
        )
        if (!f.exists()) {
            f.createNewFile()
        }

        f.inputStream().use {
            it.bufferedReader().use {
                editText.setText(it.readText())
                inited = true
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add("Save").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            setOnMenuItemClickListener {

                val ctx = context
                val fname = filename
                if (ctx != null && fname != null) {

                    val f = File(
                        ctx.getExternalFilesDir(null),
                        fname
                    )
                    if (!f.exists()) {
                        f.createNewFile()
                    }

                    f.outputStream().use {
                        it.write(editText.text.toString().toByteArray())
                    }
                    val b = Bundle().apply {
                        putBoolean("changed", true)
                        putString("filename", filename)
                    }
                    (activity as? SettingsActivity)?.fragmentResult?.setFragmentResult(
                        KEY_FILE_CHANGED,
                        b
                    )
                    Toast.makeText(ctx, "$filename Saved", Toast.LENGTH_SHORT).show()
                }
//                parentFragmentManager.popBackStack()
                activity?.title = "Edit `$filename`"
                changed = false
                onBackPressedCallback.isEnabled = changed
                true
            }
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            if (changed) {
                onBackPressedCallback.handleOnBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment EditFileFragment.
         */
        const val KEY_FILE_CHANGED = "FILE_CHANGED"

        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String) =
            EditFileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILENAME, param1)
                }
            }
    }
}