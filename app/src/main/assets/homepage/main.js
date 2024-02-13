//@ts-check

class Android1 {

    static getBookMarks() {
        return JSON.stringify(
            {
                bookmarks: [
                    {
                        title: 'chouti0',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favic'
                    },
                    {
                        title: 'chouti1',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                    {
                        title: 'chouti2',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                    {
                        title: 'chouti3',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                    {
                        title: 'chouti4',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                     {
                        title: 'chouti5',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                    {
                        title: 'chouti6',
                        url: 'https://m.chouti.com/hot/new',
                        icon_url: 'https://m.chouti.com/static/image/favicon.png'
                    },
                ]
            }
        )
    }

    static getSearchUrl() {
        return 'https://cn.bing.com/search?q='
    }

    static setBookMarks() {

    }
}

const AndroidJSInterface = typeof Android === 'undefined' ? Android1 : Android;

let changed = false
let editIng = false

/** @type {HTMLDivElement} */
let bookmarkBox = document.querySelector('#bookmark-box')

let bookmarks = JSON.parse(AndroidJSInterface.getBookMarks() || "{}")

let cachedImg = localStorage.getItem("images") || {}

updateBookmarks(bookmarks.bookmarks)

function loadDefault(e) {

    let p = bookmarks.bookmarks[e.parentElement.dataset.idx]?.default_icon

    if (p!== undefined && p!== e.src && e.dataset.twice !== 'yes') {
        e.src = p
        e.dataset.twice = 'yes'
    } else {
        e.parentElement.classList.toggle('error', true)
    }

}

function onDragStart(ev) {
    ev.dataTransfer.setData("text", ev.currentTarget.dataset.idx);
    ev.dataTransfer.effectAllowed = "move";
}

function onDragOver(ev) {
    ev.preventDefault();
    ev.dataTransfer.dropEffect = "move";
    ev.currentTarget.style.border = 'none'
    if(ev.offsetX > ev.currentTarget.clientWidth / 2) {
    ev.currentTarget.style.borderRight = '2px solid red'
//      ev.currentTarget.style.transform = 'translateX(3px)'
    } else {
        ev.currentTarget.style.borderLeft = '2px solid red'
//      ev.currentTarget.style.transform = 'translateX(-3px)'

}
}

function onDragEnd(ev) {

}

function onDragLeave(ev) {
        ev.currentTarget.style.border = 'none'

}

function onDrop(ev) {
        ev.currentTarget.style.border = 'none'
//    ev.currentTarget.style.transform = 'none'
    let offsetIdx = ev.offsetX > ev.currentTarget.clientWidth / 2 ? 1 : -1;
    ev.preventDefault();
    let idx = parseInt(ev.dataTransfer.getData("text") ?? -1)
    let tidx = parseInt(ev.currentTarget.dataset.idx ?? -1)
    console.log(idx,'->',tidx)

    if(idx >= 0 && tidx !== idx) {
        let a = bookmarks.bookmarks[idx]
        let b = bookmarks.bookmarks[tidx]

        if (a!== null && b!= null) {

            let aa = true;
            if(idx > tidx) {
                if(offsetIdx>0) tidx++;
                for (let i=idx;i>tidx;i--) {
                    bookmarks.bookmarks[i] = bookmarks.bookmarks[i-1]
                }
                bookmarks.bookmarks[tidx] = a
            } else if(idx < tidx) {
                if(offsetIdx<0) tidx--;
                for (let i=idx;i<tidx;i++) {
                    bookmarks.bookmarks[i] = bookmarks.bookmarks[+i+1]
                }
                bookmarks.bookmarks[tidx] = a

            } else {
                aa = false;
            }
            updateBookmarks(bookmarks.bookmarks)
            resetForm()
            if(aa) {
                AndroidJSInterface.setBookMarks(JSON.stringify(bookmarks))
            }
        }
    }

}

function updateBookmarks(bookmarks) {

    bookmarkBox.innerHTML = bookmarks?.reduce((pre, cur, idx) => {

        if(cur.pinned === false) return pre;
        let rect = cur.rect ? 'rect' : ''
        let icon_url = cur.icon_url || cur.default_icon
        return pre + `
        <div class="item" draggable="true" ondragleave="onDragLeave(event)" ondragstart="onDragStart(event)" ondragover="onDragOver(event)" ondrop="onDrop(event)" data-href="${cur.url}" data-idx="${idx}" >
        <div class="imgbox">
        <div class="error-img"><span>${cur.title[0]}</span></div>
        <img class="${rect}" src="${icon_url}" onerror="loadDefault(this)" alt="${cur.title[0]}">
        </div>
        <div class="edititem remove" data-idx="${idx}">X</div>
        <div class="title">${cur.title}</div>
        </div>
        
        `;
    }, "")
    
}

bookmarkBox.addEventListener('click', (ev /**@type {PointerEvent} */) => {

    if (ev.target.classList.contains('remove')) {
        changed = true
        bookmarks.bookmarks[ev.target.dataset.idx].pinned = false
        updateBookmarks(bookmarks.bookmarks)
        return
    }

    /**@type {HTMLDivElement} */
    let path = ev.composedPath().find(path => path.classList.contains('item') && path.dataset.href !== undefined)

    if (path !== undefined) {

        if(editIng) {
            let bm = bookmarks.bookmarks[path.dataset.idx]
            let icon_url = bm.icon_data_url || bm.icon_url

            siteTitle.value = bm.title
            siteTitle.dataset.idx = path.dataset.idx
            siteUrl.value = bm.url
            siteIconUrl.value = bm.icon_url
            siteIcon.src = icon_url
            siteIconRect.checked = bm.rect ?? false
            siteIcon.classList.toggle('rect', siteIconRect.checked)

            return
        }
        // window.open(path.dataset.href, '_blank')
        location.href = path.dataset.href

    }

})

const searchUrl = AndroidJSInterface.getSearchUrl() || ""

function search() {
    if (searchText.value.length > 0) {
        if(/^https?:\/\//.test(searchText.value)) {
            location.href = searchText.value
        } else {
            location.href = searchUrl + searchText.value
        }
    }
}

function resetForm() {
    siteTitle.value = ''
    siteTitle.dataset.idx = -1
    siteUrl.value = ''
    siteIconUrl.value = ''
    siteIcon.src = ''
    siteIconRect.checked = false
    siteIcon.classList.toggle('rect', siteIconRect.checked)
}

/**@type {HTMLInputElement} */
let searchText = document.querySelector('#search-text')

searchText?.addEventListener('keyup', (e) => {
    if(editIng) {
        return
    }
    if (e.key === 'Enter') {
        search()
        searchText.blur()
    }
})

let searchBtn = document.querySelector('#to-search')

searchBtn?.addEventListener('pointerdown', () => {
    if(editIng) {
        return
    }
    search()
})

let siteTitle = document.querySelector('#site-title')
let siteUrl = document.querySelector('#site-url')
let siteIcon = document.querySelector('#site-icon img')
let siteIconRect = document.querySelector('#rect')

siteIconRect.addEventListener('change',()=>{
    siteIcon.classList.toggle('rect', siteIconRect.checked)
})

let siteIconUrl = document.querySelector('#site-icon-url')

siteIconUrl?.addEventListener('blur', ()=>{
    siteIcon.src = siteIconUrl.value.trim()

})

let addBtn = document.querySelector('#add')

addBtn?.addEventListener('click', () => {

    if (siteTitle.value.length > 0 && siteUrl.value.length > 0) {

        changed = true
        let idx = parseInt(siteTitle.dataset.idx)

            let p = bookmarks.bookmarks[idx]
        if (idx !== NaN && p !== undefined) {

            p.title = siteTitle.value
            p.url = siteUrl.value
            p.icon_url = siteIconUrl.value
            p.pinned= true
            p.rect = siteIconRect.checked ?? false

        } else {
            bookmarks.bookmarks.push(
                {
                    title: siteTitle.value,
                    url: siteUrl.value,
                    icon_url: siteIconUrl.value,
                    default_icon: '',
                    pinned: true,
                    rect: siteIconRect.checked ?? false
                }
            );
        }
        updateBookmarks(bookmarks.bookmarks)
        resetForm()

        //siteIcon.src = siteTitle.value = siteUrl.value = siteIconUrl.value = ''
    }

})

let editBtn = document.querySelector('#edit')

editBtn?.addEventListener('click',()=>{
    editIng = !editIng
    document.body.classList.toggle('editing', editIng)
    editBtn.textContent = editIng ? 'OK': 'O'

    resetForm();

    if (changed && !editIng) {
        AndroidJSInterface.setBookMarks(JSON.stringify(bookmarks))
    }
})