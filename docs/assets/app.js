(function () {
  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function buildLog() {
    var target = document.querySelector("[data-log-lines]");
    if (!target) return;
    var levels = [["INFO", "info"], ["WARNING", ""], ["INFO", "info"], ["ERROR", "err"], ["INFO", "info"]];
    var messages = [
      "users.py:41 - resolved candidate for @ryuu_king",
      "sync.py:88 - fetched 120 updates",
      "media.py:12 - cached 3 thumbnails",
      "net.py:203 - retrying in 5s",
      "users.py:41 - could not resolve candidate",
      "db.py:57 - flushed 42 rows",
      "bot.py:19 - command /start from 7913990514"
    ];
    var html = "";
    for (var i = 0; i < 190; i++) {
      var level = levels[i % levels.length];
      var seconds = (14 * 60 + i * 5);
      var hh = 18, mm = Math.floor(seconds / 60) % 60, ss = seconds % 60;
      var stamp = "2026-10-03 " + hh + ":" + (mm < 10 ? "0" : "") + mm + ":" + (ss < 10 ? "0" : "") + ss;
      html += '<div class="code-line"><span class="n">' + (i * 16 + 1).toLocaleString("en-US") + '</span><span class="t">' + stamp +
        '</span><span class="lv ' + level[1] + '">' + level[0] + '</span><span class="m">' + messages[i % messages.length] + "</span></div>";
    }
    target.innerHTML = html;
  }

  function buildAccounts() {
    var list = document.querySelector("[data-accounts]");
    if (!list) return;
    var people = [
      ["Arjun", "#4aa3f5", "#2f7fe6"], ["Work", "#3cc4b4", "#1ea596"], ["Meera", "#f7a541", "#ef7d2d"],
      ["Riya", "#f06ba0", "#dd4a82"], ["Gaming", "#a77bf7", "#8457ea"], ["Kabir", "#f2655a", "#e2453c"],
      ["Shop", "#7bc862", "#4fae3c"], ["Anya", "#6ec9f7", "#3aa6e0"], ["News", "#f7c341", "#e6a21d"],
      ["Dev", "#9e86ff", "#7b5ff0"]
    ];
    var html = '<li class="highlight" aria-hidden="true"></li>';
    people.forEach(function (p) {
      html += '<li><span class="avatar" style="--c1:' + p[1] + ";--c2:" + p[2] + '">' + p[0].charAt(0) + "</span>" + p[0] + "</li>";
    });
    list.innerHTML = html;
  }

  function setupDemo() {
    var scenes = Array.prototype.slice.call(document.querySelectorAll(".scene"));
    var tabs = Array.prototype.slice.call(document.querySelectorAll(".scene-tabs [data-go]"));
    if (!scenes.length) return;
    var index = 0;
    var timer = null;
    var auto = !reduceMotion;
    var visible = true;

    function show(next) {
      index = next;
      scenes.forEach(function (scene, i) {
        var active = i === next;
        scene.classList.toggle("is-active", active);
        scene.classList.remove("play");
        scene.setAttribute("aria-hidden", active ? "false" : "true");
      });
      void scenes[next].offsetWidth;
      scenes[next].classList.add("play");
      tabs.forEach(function (tab, i) {
        tab.setAttribute("aria-selected", i === next ? "true" : "false");
      });
    }

    function schedule() {
      clearTimeout(timer);
      if (!auto || !visible || document.hidden) return;
      timer = setTimeout(function () {
        show((index + 1) % scenes.length);
        schedule();
      }, 7200);
    }

    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () {
        auto = false;
        clearTimeout(timer);
        show(i);
      });
    });

    if ("IntersectionObserver" in window) {
      new IntersectionObserver(function (entries) {
        visible = entries[0].isIntersecting;
        schedule();
      }, { threshold: 0.25 }).observe(document.querySelector(".phone"));
    }
    document.addEventListener("visibilitychange", schedule);

    show(0);
    schedule();
  }

  function formatDate(iso) {
    var date = new Date(iso + "T00:00:00Z");
    if (isNaN(date.getTime())) return iso;
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" });
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function notesHtml(notes) {
    if (!notes || !notes.length) return "<li>Improvements and fixes.</li>";
    return notes.map(function (note) { return "<li>" + escapeHtml(note) + "</li>"; }).join("");
  }

  function loadUpdates() {
    if (!window.fetch) return;
    fetch("updates.json", { cache: "no-cache" })
      .then(function (response) { return response.ok ? response.json() : null; })
      .then(function (data) {
        if (!data || !data.updates || !data.updates.length) return;
        var latest = data.updates[0];
        document.querySelectorAll("[data-latest-version]").forEach(function (el) { el.textContent = latest.version; });
        document.querySelectorAll("[data-latest-date]").forEach(function (el) {
          el.textContent = formatDate(latest.date);
          if (el.tagName === "TIME") el.setAttribute("datetime", latest.date);
        });
        var notes = document.querySelector("[data-latest-notes]");
        if (notes) notes.innerHTML = notesHtml(latest.notes);
        if (data.app && data.app.minAndroid) {
          document.querySelectorAll("[data-min-android]").forEach(function (el) { el.textContent = data.app.minAndroid; });
        }
        var earlier = data.updates.slice(1);
        var details = document.querySelector("[data-earlier]");
        var list = document.querySelector("[data-earlier-list]");
        if (details && list && earlier.length) {
          list.innerHTML = earlier.map(function (u) {
            return '<article class="release"><header><h3>Version ' + escapeHtml(u.version) + '</h3><time datetime="' + escapeHtml(u.date) + '">' +
              escapeHtml(formatDate(u.date)) + "</time></header><ul>" + notesHtml(u.notes) + "</ul></article>";
          }).join("");
          details.hidden = false;
        }
      })
      .catch(function () {});
  }

  function setupTilt() {
    var phone = document.querySelector(".phone-tilt .phone");
    var area = document.querySelector(".demo-section");
    if (!phone || !area || reduceMotion) return;
    area.addEventListener("pointermove", function (e) {
      var rect = phone.getBoundingClientRect();
      var dx = (e.clientX - (rect.left + rect.width / 2)) / window.innerWidth;
      var dy = (e.clientY - (rect.top + rect.height / 2)) / window.innerHeight;
      phone.style.setProperty("--ry", (dx * 36).toFixed(2) + "deg");
      phone.style.setProperty("--rx", (-dy * 22).toFixed(2) + "deg");
    });
    area.addEventListener("pointerleave", function () {
      phone.style.removeProperty("--ry");
      phone.style.removeProperty("--rx");
    });
  }

  buildLog();
  buildAccounts();
  setupDemo();
  setupTilt();
  loadUpdates();
  var year = document.querySelector("[data-year]");
  if (year) year.textContent = new Date().getFullYear();
})();
