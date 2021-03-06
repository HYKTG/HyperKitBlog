import Valid from './valid';
import Net from './net';
import Srp from './srp';
import stor from './stor';

window.Valid = Valid;
window.Net = Net;
window.Srp = Srp;
window.stor = stor;

import 'bootstrap';
import 'pace';

let uInfo = (id) => stor("user:" + id);

let getUserInfo = async (uid) => {
    let c = uInfo(uid)();
    if (c) return c;
    let r = await Net.getUnwrap("/user/" + uid + "/");
    if(r.status !== 200) {
        return null;
    }
    let ud = r.data;
    uInfo(uid)(ud);
    return ud;
}

let searchSubmit = $("#searchSubmit");
let searchForm = $("#searchForm");
if (searchSubmit && searchForm) {
    searchSubmit.click(() => {
        window.location.href = "/index.html?q=" + searchForm.val();
    });
}

window.uInfo = uInfo;
window.getUserInfo = getUserInfo;

$(() => {
    if (Srp.isSignined()) {
        $(".signin-hide").hide();
        $(".signin-show").show();
    }
});