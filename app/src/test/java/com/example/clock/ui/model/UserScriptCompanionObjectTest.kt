package com.example.clock.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UserScriptCompanionObjectTest {

    @Test
    fun parse() {

        val s = """
                // ==UserScript==
                // @name        idabc
                // @namespace   MyScripts
                // @match       http://example.com
                // @author      -
                // ==/UserScript==
                "use strict";
                
                """.trimIndent()

        val us = UserScript.parse(s)

        assertNotNull(us)

        assertEquals(us!!.name, "idabc")
        assertEquals(us.namespace, "MyScripts")
        assertEquals(us.match, setOf("http://example.com"))
        assertEquals(us.content, s)
    }


    @Test
    fun parse2() {

        val s = """
                // ==UserScript==
                // @match       http://example.com
                // @match       http://www.example.com
                // @namespace   My Scripts
                // @name        id abc
                // @author      -
                // ==/UserScript==
                "use strict";
                
                {
                
                }
                """.trimIndent()

        val us = UserScript.parse(s)

        assertNotNull(us)


        assertEquals(us!!.name, "id abc")
        assertEquals(us.namespace, "My Scripts")
        assertEquals(us.match, setOf("http://example.com", "http://www.example.com"))
        assertEquals(us.content, s)
    }

    @Test
    fun parse_space() {

        val s = """
                // ==UserScript==
                // @name        idabc
                // @namespace   MyScripts
                // @match       http://example.com
                // @author      -
                //==/UserScript==
                "use strict";
                
                """.trimIndent()

        val us = UserScript.parse(s)

        assertNull(us)
    }

    @Test
    fun parse_no_name() {

        val s = """
                // ==UserScript==
                // @match       http://example.com
                // @match       http://www.example.com
                // @namespace   My Scripts

                // @author      -
                // ==/UserScript==
                "use strict";
                
                {
                
                }
                """.trimIndent()

        val us = UserScript.parse(s)

        assertNull(us)
    }
}