package com.example.clock.ui.main

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

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filename = it.getString(ARG_FILENAME)
        }
        setHasOptionsMenu(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        editText = inflater.inflate(R.layout.fragment_edit_file, container, false) as EditText

        editText.addTextChangedListener {
            if (inited) {
                changed = true
                activity?.title = "Edit `$filename` Changed"
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
                true
            }
        }

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