package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.QuestRepository
import com.eventmap.server.presentation.dto.BusinessDocDto
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

// =====================================================================
//  Мини-админка модерации (MVP до полноценных ролей):
//  GET /admin                 — самодостаточная HTML-страница (ключ вводится на ней)
//  GET /admin/api/business    — заявки бизнеса по статусу (X-Admin-Key)
//  GET /admin/api/quests      — квесты по статусу (X-Admin-Key)
//  Решения принимают УЖЕ существующие ручки:
//  POST /admin/business/{id}/review  ·  POST /admin/quests/{id}/review
// =====================================================================

@Serializable
data class AdminBusinessDto(
    val id: String, val status: String, val rejectReason: String? = null,
    val bin: String, val legalName: String, val brandName: String, val category: String,
    val phone: String? = null, val social: String? = null,
    val documents: List<BusinessDocDto> = emptyList(),
)

private fun BusinessAccount.toAdminDto() = AdminBusinessDto(
    id = id, status = status, rejectReason = rejectReason,
    bin = bin, legalName = legalName, brandName = brandName, category = category,
    phone = phone, social = social,
    documents = documents.map { BusinessDocDto(it.kind, it.fileUrl) },
)

fun Route.adminRoutes(
    business: BusinessRepository,
    quests: QuestRepository,
    adminKey: String,
) {
    get("/admin") {
        call.respondText(ADMIN_HTML, ContentType.Text.Html)
    }

    get("/admin/api/business") {
        if (!call.adminOk(adminKey)) return@get call.forbidden()
        val status = call.request.queryParameters["status"] ?: "pending"
        call.respond(business.listByStatus(status).map { it.toAdminDto() })
    }

    get("/admin/api/quests") {
        if (!call.adminOk(adminKey)) return@get call.forbidden()
        val status = call.request.queryParameters["status"] ?: "moderation"
        call.respond(quests.listByStatus(status).map { it.toDto() })
    }
}

private fun ApplicationCall.adminOk(key: String): Boolean = request.header("X-Admin-Key") == key

private suspend fun ApplicationCall.forbidden() =
    respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("FORBIDDEN", "Неверный админ-ключ")))

// Страница без сборки и зависимостей: vanilla JS, ключ хранится в sessionStorage.
// ВАЖНО: в JS нет ни одного символа доллара — иначе Kotlin-интерполяция сломает файл.
private val ADMIN_HTML = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>EventMap · Модерация</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #0A0F1E; color: #E8ECF4; font: 14px/1.45 system-ui, -apple-system, sans-serif; }
  header { display: flex; gap: 12px; align-items: center; padding: 16px 20px; border-bottom: 1px solid rgba(255,255,255,.08); position: sticky; top: 0; background: #0A0F1E; }
  header h1 { font-size: 16px; margin: 0; flex: 1; }
  input, select { background: #141B2E; color: #E8ECF4; border: 1px solid rgba(255,255,255,.12); border-radius: 8px; padding: 8px 10px; font-size: 13px; }
  main { max-width: 960px; margin: 0 auto; padding: 20px; }
  h2 { font-size: 13px; letter-spacing: .08em; color: #8A93A6; text-transform: uppercase; margin: 26px 0 10px; }
  .card { background: #141B2E; border: 1px solid rgba(255,255,255,.07); border-radius: 14px; padding: 14px 16px; margin-bottom: 10px; }
  .row { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
  .name { font-weight: 700; font-size: 15px; }
  .muted { color: #8A93A6; font-size: 12px; }
  .docs a { color: #9D8CFF; font-size: 12px; margin-right: 10px; }
  .actions { margin-top: 10px; display: flex; gap: 8px; }
  button { border: 0; border-radius: 8px; padding: 8px 14px; font-size: 13px; font-weight: 600; cursor: pointer; }
  .ok { background: #1F8A5D; color: #fff; }
  .no { background: #7A2E3B; color: #fff; }
  .empty { color: #56657A; padding: 18px 4px; }
  .pill { background: rgba(124,92,255,.18); color: #B7A8FF; border-radius: 999px; padding: 2px 10px; font-size: 11px; }
  .err { color: #FF7A8A; padding: 8px 0; }
</style>
</head>
<body>
<header>
  <h1>EventMap · Модерация</h1>
  <input id="key" type="password" placeholder="Админ-ключ" style="width:180px">
  <button class="ok" onclick="saveKey()">Войти</button>
</header>
<main>
  <h2>Заявки бизнеса
    <select id="bizStatus" onchange="loadBiz()">
      <option value="pending" selected>на модерации</option>
      <option value="approved">одобренные</option>
      <option value="rejected">отклонённые</option>
    </select>
  </h2>
  <div id="biz"><div class="empty">Введи ключ и нажми «Войти»</div></div>

  <h2>Квесты
    <select id="questStatus" onchange="loadQuests()">
      <option value="moderation" selected>на модерации</option>
      <option value="active">активные</option>
      <option value="rejected">отклонённые</option>
    </select>
  </h2>
  <div id="quests"><div class="empty">—</div></div>
</main>
<script>
  var keyEl = document.getElementById('key');
  keyEl.value = sessionStorage.getItem('adminKey') || '';

  function hdrs() { return { 'X-Admin-Key': keyEl.value, 'Content-Type': 'application/json' }; }

  function saveKey() {
    sessionStorage.setItem('adminKey', keyEl.value);
    loadBiz(); loadQuests();
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function fail(el, msg) { el.innerHTML = '<div class="err">' + esc(msg) + '</div>'; }

  function loadBiz() {
    var el = document.getElementById('biz');
    var st = document.getElementById('bizStatus').value;
    fetch('/admin/api/business?status=' + st, { headers: hdrs() })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status + ' — проверь ключ'); return r.json(); })
      .then(function (list) {
        if (!list.length) { el.innerHTML = '<div class="empty">Пусто</div>'; return; }
        el.innerHTML = list.map(function (b) {
          var docs = (b.documents || []).map(function (d, i) {
            return '<a href="' + esc(d.fileUrl) + '" target="_blank">' + esc(d.kind || ('документ ' + (i + 1))) + '</a>';
          }).join('');
          var reason = b.rejectReason ? '<div class="muted">Причина: ' + esc(b.rejectReason) + '</div>' : '';
          var actions = st === 'pending'
            ? '<div class="actions">' +
              '<button class="ok" onclick="reviewBiz(\'' + b.id + '\', true)">Одобрить</button>' +
              '<button class="no" onclick="reviewBiz(\'' + b.id + '\', false)">Отклонить</button></div>'
            : '';
          return '<div class="card">' +
            '<div class="row"><span class="name">' + esc(b.brandName) + '</span>' +
            '<span class="pill">' + esc(b.category) + '</span>' +
            '<span class="muted">' + esc(b.legalName) + ' · БИН ' + esc(b.bin) + '</span></div>' +
            '<div class="muted">' + esc(b.phone || '') + ' ' + esc(b.social || '') + '</div>' +
            '<div class="docs">' + (docs || '<span class="muted">без документов</span>') + '</div>' +
            reason + actions + '</div>';
        }).join('');
      })
      .catch(function (e) { fail(el, e.message); });
  }

  function reviewBiz(id, ok) {
    var body = { decision: ok ? 'approved' : 'rejected' };
    if (!ok) {
      var reason = prompt('Причина отказа (увидит заявитель):');
      if (reason === null) return;
      body.reason = reason;
    }
    fetch('/admin/business/' + id + '/review', { method: 'POST', headers: hdrs(), body: JSON.stringify(body) })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); loadBiz(); })
      .catch(function (e) { alert(e.message); });
  }

  function loadQuests() {
    var el = document.getElementById('quests');
    var st = document.getElementById('questStatus').value;
    fetch('/admin/api/quests?status=' + st, { headers: hdrs() })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status + ' — проверь ключ'); return r.json(); })
      .then(function (list) {
        if (!list.length) { el.innerHTML = '<div class="empty">Пусто</div>'; return; }
        el.innerHTML = list.map(function (q) {
          var pts = (q.points || []).map(function (p) {
            var icon = p.condition === 'photo' ? '📸' : (p.condition === 'purchase' ? '🧾' : '📍');
            return icon + ' ' + esc(p.placeName || (p.lat.toFixed(4) + ', ' + p.lng.toFixed(4)));
          }).join(' · ');
          var actions = st === 'moderation'
            ? '<div class="actions">' +
              '<button class="ok" onclick="reviewQuest(\'' + q.id + '\', true)">Одобрить</button>' +
              '<button class="no" onclick="reviewQuest(\'' + q.id + '\', false)">Отклонить</button></div>'
            : '';
          return '<div class="card">' +
            '<div class="row"><span class="name">' + esc(q.emoji || '🎮') + ' ' + esc(q.title) + '</span>' +
            '<span class="muted">от ' + esc(q.businessBrand) + '</span></div>' +
            '<div class="muted">' + pts + '</div>' +
            '<div class="muted">Приз: ' + esc(q.prizeTitle) + (q.endsAt ? ' · до ' + esc(String(q.endsAt).slice(0, 10)) : '') + '</div>' +
            actions + '</div>';
        }).join('');
      })
      .catch(function (e) { fail(el, e.message); });
  }

  function reviewQuest(id, ok) {
    fetch('/admin/quests/' + id + '/review', { method: 'POST', headers: hdrs(), body: JSON.stringify({ decision: ok ? 'approved' : 'rejected' }) })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); loadQuests(); })
      .catch(function (e) { alert(e.message); });
  }

  if (keyEl.value) { loadBiz(); loadQuests(); }
</script>
</body>
</html>
"""
