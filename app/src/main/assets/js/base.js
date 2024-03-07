
//@ts-check

let store = {}

let changeCallbacks = []

let changeCallbacksId = 1

function GM_getValue(key, defaultValue) {
    if (key in store) {
        return store[key];
    } else {
        return defaultValue;
    }
}

function GM_setValue(key, value) {
    let oldValue = store[key]
    store[key] = value
    changeCallbacks.filter(i => i.name === key).forEach(f => f.callback(key, oldValue, value, false))
}

function GM_deleteValue(key) {
    let oldValue = store[key]
    delete store[key]
    changeCallbacks.filter(i => i.name === key).forEach(f => f.callback(key, oldValue, undefined, false))
}

function GM_listValues() {
    return Object.keys(store)
}

function GM_addValueChangeListener(name, callback) {
    let id = changeCallbacksId++
    changeCallbacks[id] = {
        name, callback
    }

    return id.toString()
}

function GM_removeValueChangeListener(listenerId) {
    delete changeCallbacks[+listenerId]
}


function GM_getResourceText(name) {
    return undefined
}

function GM_getResourceURL(name) {
    return undefined
}

function GM_info() {
    return ''
}

function GM_openInTab(url, options) {

}

function GM_xmlHttpRequest(details) {

    fetch(details.url, details).then(r => {
      return  r.text().then(s=>{

            r.responseText = s
            details.onload(r)
        })

    }).catch(e => {
        details.onerror(e)
    })

}

function GM_addStyle(css) {
    let style = document.createElement('style')
    style.textContent = css
    document.head.append(style)
    return style
}

var GM = {
    getValue: GM_getValue,
    setValue: GM_setValue,
    info: GM_info,
    getResourceURL: GM_getResourceURL,
    xmlHttpRequest: GM_xmlHttpRequest,
    addStyle: GM_addStyle,
}