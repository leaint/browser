package com.example.clock.ui.main

import android.app.AlertDialog
import android.app.Fragment
import android.database.DataSetObserver
import android.os.Bundle
import android.util.ArrayMap
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import com.example.clock.R
import com.example.clock.SettingsActivity
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.ui.model.UserScript
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * A fragment representing a list of Items.
 */
class UserScriptFragment : Fragment() {

    private lateinit var adapter: ArrayAdapter<String>

    private val scriptMap = ArrayMap<String, String>()
    private var changed = false

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        val ctx = context
        run {
            val f = File(ctx.getExternalFilesDir(null), USER_SCRIPT_FILE)
            if (!f.exists()) {
                f.createNewFile()
            }

            f.inputStream().use { input ->
                run {

                    val buf = Files.readAllBytes(f.toPath())

                    GlobalWebViewSetting.parseUserScriptFile(buf, scriptMap)
                }
            }
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onStart() {
        super.onStart()

        (activity as? SettingsActivity)?.fragmentResult?.setFragmentResultListener(US_CHANGED) { k, b ->
            run {

                b.getString("script")?.let {

                    UserScript.parse(it)?.let { us ->
                        run {

                            scriptMap["${us.name} - ${us.namespace}"] = it
                            adapter.clear()
                            adapter.addAll(scriptMap.keys.toList())

                        }
                    }

                }

            }
        }


    }

    @Deprecated("Deprecated in Java")
    override fun onStop() {
        super.onStop()

        if (changed) {
            val ctx = context ?: return
            val f = File(ctx.getExternalFilesDir(null), USER_SCRIPT_FILE)
            if (!f.exists()) {
                f.createNewFile()
            }
            f.outputStream().use {
                it.write(GlobalWebViewSetting.stringifyUserScriptFile(scriptMap).toByteArray())
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.userscript_item_list, container, false) as ListView

        val ctx = context
        if (ctx != null) {

            adapter = ArrayAdapter(
                ctx,
                R.layout.simple_list_item_checked,
                scriptMap.keys.toMutableList()
            ).apply {
                registerDataSetObserver(object : DataSetObserver() {
                    override fun onChanged() {
                        changed = true
                    }
                })
            }
            view.setOnItemClickListener { parent, view, position, id ->


                run {
                    scriptMap[adapter.getItem(position)]?.let {
                        val fragment = EditUserScriptFragment.newInstance(
                            it
                        )
                        fragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.settings, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            }


            view.setOnCreateContextMenuListener { menu, v, menuInfo ->
                run {

                    menu.add("Remove").apply {
                        setOnMenuItemClickListener {

                            return@setOnMenuItemClickListener true
                        }
                    }
                }
            }
            view.adapter = adapter

            view.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            view.setMultiChoiceModeListener(
                object : MultiChoiceModeListener {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        mode?.title = "Remove some user script?"
                        menu?.add(0, 0, 0, "Remove")?.apply {
                            setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT or MenuItem.SHOW_AS_ACTION_IF_ROOM)

                        }
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        when (item?.itemId) {
                            0 -> {
                                val count = adapter.count
                                val checked = view.checkedItemPositions
                                for (i in 0 until count) {
                                    if (checked.get(i)) {
                                        scriptMap.remove(adapter.getItem(i))
                                    }
                                }
                                mode?.finish()
                                adapter.clear()
                                adapter.addAll(scriptMap.keys.toList())
                                adapter.notifyDataSetChanged()
                            }

                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {

                    }

                    override fun onItemCheckedStateChanged(
                        mode: ActionMode?,
                        position: Int,
                        id: Long,
                        checked: Boolean
                    ) {

                    }

                }
            )


        }

        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onResume() {
        super.onResume()

        activity?.title = "User Script"

    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add("Add").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            setOnMenuItemClickListener {
                val fragment = EditUserScriptFragment()
                fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.settings, fragment)
                    .addToBackStack(null)
                    .commit()

                true
            }
        }
    }

    companion object {

        // TODO: Customize parameter argument names
        const val US_CHANGED = "US_CHANGED"
        const val USER_SCRIPT_FILE = "user_script.json"

        // TODO: Customize parameter initialization
        @JvmStatic
        fun newInstance() =
            UserScriptFragment()
    }
}

class EditUserScriptFragment : Fragment() {

    private lateinit var editText: EditText

    private var changed = false
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_edit_userscript, container, false) as EditText

        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        onBackPressedCallback.isEnabled = changed
        editText = view
        val initText = arguments?.getString(ARG_US)

        if (initText.isNullOrBlank()) {
            val id = UUID.randomUUID().toString()
            val text = """
                // ==UserScript==
                // @name        New script - $id
                // @namespace   MyScripts
                // @match       http://
                // @author      -
                // ==/UserScript==
                "use strict";
                
                """.trimIndent()
            editText.setText(text)
        } else {
            editText.setText(initText)
        }
        editText.addTextChangedListener {
            activity?.title = "Edit User Script *"
            changed = true
            onBackPressedCallback.isEnabled = changed
        }
        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onResume() {
        super.onResume()
        activity?.title = "Edit User Script"
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.groupId == 1) {
            val id = item.itemId - 10
            if (id in SNIPPETS.indices) {
                editText.append("\n" + SNIPPETS[id].second)
            }
        } else if (item.itemId == android.R.id.home) {
            if (changed) {
                onBackPressedCallback.handleOnBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add("Save").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            setOnMenuItemClickListener {
                val b = Bundle().apply {

                    putString("script", editText.text.toString())
                }
                (activity as? SettingsActivity)?.fragmentResult?.setFragmentResult(
                    UserScriptFragment.US_CHANGED,
                    b
                )
//                setFragmentResult(UserScriptFragment.US_CHANGED, b)

                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()

//                parentFragmentManager.popBackStack()

                changed = false
                onBackPressedCallback.isEnabled = changed

                true

            }
        }
        var i = 10
        for ((k, _) in SNIPPETS) {
            menu.add(1, i++, 0, k)
        }
    }

    companion object {

        // TODO: Customize parameter argument names
        const val ARG_US = "user-script"

        val SNIPPETS = arrayOf(

            "requestIdleCallback" to """
            window.requestIdleCallback(()=>{



            });
            """.trimIndent(),

            "requestAnimationFrame" to """
                  requestAnimationFrame(()=>{
                  
                  
                  
                  });
                  """.trimIndent(),

            "Style" to """
                let st = document.createElement('style');
                st.textContent = `

                #something {

                display: none !important;

                }

                `;
                document.head.appendChild(st);

            """.trimIndent(),

            "Script" to """
                let script = document.createElement('script');
                script.src='';
                script.text = `
                
                `;
                document.body.appendChild(script);
            """.trimIndent(),

            "Html" to """
                /* beforebegin afterbegin beforeend afterend */
                document.body.insertAdjacentHTML('beforeend', `<div></div>`);
            """.trimIndent(),

            "setInterval" to """"
                var maxCount = 10;
                var _id = setInterval(()=>{
                
                
                if(maxCount-- < 0) {
                clearInterval(_id);
                }
                
                }, 1000);
                """.trimIndent(),

            "setTimeout" to """
                setTimeout(()=>{


                }, 1000);
            """.trimIndent(),

            "onload" to """
                document.addEventListener("readystatechange", ()=> {
                        if (document.readyState === 'interactive') {




                        } else if(document.readyState === 'complete') {



                        }  
                });
            """.trimIndent(),
            "MutationObserver" to """
                function logChanges(records, observer) {
                    for (const record of records) {
                        for (const addedNode of record.addedNodes) {
                        
                        }

                        for (const removedNode of record.removedNodes) {
                        
                        }

                        if (record.target.childNodes.length === 0) {
                        
                        }
                    }
                }

                const observerOptions = {
                    childList: true,
                    subtree: true,
                };

                const container = document.querySelector("container");
                const observer = new MutationObserver(logChanges);
                observer.observe(container, observerOptions);

            """.trimIndent(),
            "doWall" to """
                function createWallCall(func,w=2000) {

                    let n = 0;
                    const wall = w;

                    const d = ()=>{
                        n -= wall;
                        console.log(1);
                        func();
                    }

                    const f = ()=>{
                        const now = Date.now();
                        if (now - n < wall) {
                            return;
                        }
                        n = now;
                        requestIdleCallback(d);
                        /*setTimeout(d,0);*/
                    }

                    return f;
                }

            """.trimIndent(),
            "Proxy" to """
                document.createElement = new Proxy(document.createElement, {
                    apply: (target,that,args)=>{
                        try {
                            if (args?.length > 0 && args[0] === 'iframe') {
                                console.log('block iframe');
                                return null;
                            }
                        } catch (e) {      
                            console.error(e);
                        }
                        return target.apply(that, args);
                    }
                });
            """.trimIndent(),

            )

        @JvmStatic
        fun newInstance(userScript: String) =
            EditUserScriptFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_US, userScript)
                }
            }
    }
}
