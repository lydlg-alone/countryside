document.addEventListener('DOMContentLoaded', ()=>{
  console.log('app.js loaded');
  const statusEl = document.getElementById('status');
  const refresh = document.getElementById('refresh');
  const modulesEl = document.getElementById('modules');
  const moduleContent = document.getElementById('moduleContent');
  const mainTitle = document.getElementById('mainTitle');
  const API_BASE = (window.API_BASE ?? 'http://localhost:8080');
  let currentModule = null;

  function activeContainer(){
    if(moduleContent) return moduleContent;
    if(currentModule){
      const map = { users: 'user', status: 'dashboard' };
      const id = map[currentModule] || currentModule;
      const el = document.getElementById(id);
      if(el) return el;
    }
    return document.querySelector('.right-content') || document.body;
  }
  // modules ordered to match village.md core functional areas
  const modules = [
    {id:'status', title:'系统状态'},
    {id:'users', title:'用户与权限'},
    {id:'finance', title:'财务与收支'},
    {id:'warning', title:'预警管理'},
    {id:'governance', title:'基层治理'},
    {id:'feedback', title:'民情反馈'},
    {id:'industry', title:'产业看板'},
    {id:'ai', title:'AI 助手'},
    {id:'ops', title:'运维与审计'}
  ];

  function renderModuleList(){
    modulesEl.innerHTML = '';
    modules.forEach(m=>{
      const btn = document.createElement('button');
      btn.className = 'module-btn';
      btn.textContent = m.title;
      btn.dataset.module = m.id;
      btn.addEventListener('click', ()=>{
        document.querySelectorAll('.module-btn').forEach(b=>b.classList.remove('active'));
        btn.classList.add('active');
        openModule(m.id);
      });
      modulesEl.appendChild(btn);
    });
    const first = modulesEl.querySelector('button');
    if(first) { first.classList.add('active'); openModule('status'); }
  }

  async function checkBackend(){
    statusEl.textContent = '检测中...';
    try{
      const r = await fetch(API_BASE + '/api/', {cache:'no-store'});
      const text = await r.text();
      statusEl.textContent = '后端响应：' + text;
      statusEl.style.color = 'green';
    }catch(err){
      statusEl.textContent = '无法连接后端（请确保后端已启动）';
      statusEl.style.color = 'red';
    }
  }

  async function openModule(id){
    currentModule = id;
    mainTitle.textContent = (modules.find(m=>m.id===id)||{}).title || '模块';
    activeContainer().innerHTML = '<div class="muted">加载中...</div>';

    if(id === 'status'){
      await checkBackend();
      activeContainer().innerHTML = '<div class="muted">后端健康检查已完成。使用左侧项切换模块。</div>';
      return;
    }

    if(id === 'users'){
      await loadUsers();
      return;
    }

    if(id === 'finance'){
      await loadFinance();
      return;
    }

    if(id === 'warning'){
      await loadWarnings();
      return;
    }

      if(id === 'governance'){
        await loadGovernance();
        return;
      }

      if(id === 'feedback'){
        await loadFeedback();
        return;
      }

      if(id === 'industry'){
        await loadIndustry();
        return;
      }

      if(id === 'ai'){
        await loadAi();
        return;
      }

      if(id === 'ops'){
        await loadOps();
        return;
      }

    activeContainer().innerHTML = `<h3>${mainTitle.textContent}</h3><p class="muted">此处为 ${mainTitle.textContent} 的示例界面，可进一步实现 CRUD、图表和告警规则配置。</p>`;
  }

  // --- Users module ---
  async function loadUsers(){
    mainTitle.textContent = '用户管理';
    activeContainer().innerHTML = '<div class="muted">加载用户...</div>';
    try{
      const r = await fetch(API_BASE + '/api/users', {cache:'no-store'});
      if(!r.ok) throw new Error('no users');
      const users = await r.json();
      renderUserList(users, false);
    }catch(e){
      const mock = [
        {id:1,name:'张三',role:'管理员'},
        {id:2,name:'李四',role:'普通用户'}
      ];
      renderUserList(mock, true);
    }
  }

  function renderUserList(users, isMock){
    const wrapper = document.createElement('div');
    const note = document.createElement('div');
    note.className = 'module-list-note';
    note.textContent = isMock ? '使用本地模拟数据（后端 `/api/users` 不可用）' : '来自后端的用户数据';
    wrapper.appendChild(note);

    const actions = document.createElement('div');
    actions.style.margin = '8px 0';
    const addBtn = document.createElement('button');
    addBtn.textContent = '新增用户';
    addBtn.addEventListener('click', ()=>{ renderUserForm(); });
    actions.appendChild(addBtn);
    wrapper.appendChild(actions);

    const table = document.createElement('table');
    table.style.width = '100%';
    table.style.borderCollapse = 'collapse';
    table.innerHTML = `
      <thead><tr><th style="text-align:left">ID</th><th style="text-align:left">姓名</th><th style="text-align:left">角色</th><th style="text-align:left">操作</th></tr></thead>
      <tbody></tbody>
    `;
    const tbody = table.querySelector('tbody');
    users.forEach(u=>{
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${u.id}</td><td>${u.name}</td><td>${u.role||'-'}</td><td></td>`;
      const opTd = tr.querySelector('td:last-child');
      const edit = document.createElement('button'); edit.textContent = '编辑';
      edit.addEventListener('click', ()=> renderUserForm(u));
      const del = document.createElement('button'); del.textContent = '删除';
      del.style.marginLeft = '8px';
      del.addEventListener('click', ()=> confirmDeleteUser(u.id));
      opTd.appendChild(edit); opTd.appendChild(del);
      tbody.appendChild(tr);
    });
    wrapper.appendChild(table);
    const cont = activeContainer(); cont.innerHTML = ''; cont.appendChild(wrapper);
  }

  function renderUserForm(user){
    const isEdit = !!user;
    const form = document.createElement('form');
    form.className = 'simple-form';
    form.innerHTML = `
      <label>姓名 <input name="name" required></label>
      <label>角色 <input name="role"></label>
      <div><button type="submit">${isEdit? '保存修改':'创建用户'}</button> <button type="button" id="cancel">取消</button></div>
    `;
    if(isEdit){ form.name.value = user.name; form.role.value = user.role || ''; }
    form.addEventListener('submit', async (e)=>{
      e.preventDefault();
      const payload = { name: form.name.value, role: form.role.value };
      try{
        if(isEdit){
          const r = await fetch(API_BASE + '/api/users/'+user.id, {method:'PUT',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
          if(!r.ok) throw new Error('更新失败');
        }else{
          const r = await fetch(API_BASE + '/api/users', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
          if(!r.ok) throw new Error('创建失败');
        }
        await loadUsers();
      }catch(err){
        alert('操作失败：'+err.message);
      }
    });
    form.querySelector('#cancel').addEventListener('click', ()=> loadUsers());
    const cont = activeContainer(); cont.innerHTML = ''; cont.appendChild(form);
  }

  async function confirmDeleteUser(id){
    if(!confirm('确认删除用户 ID='+id+' 吗？')) return;
    try{
      const r = await fetch(API_BASE + '/api/users/'+id, {method:'DELETE'});
      if(!r.ok) throw new Error('删除失败');
      await loadUsers();
    }catch(err){ alert('删除失败：'+err.message); }
  }

  // --- Finance module ---
  async function loadFinance(){
    mainTitle.textContent = '财务管理';
    activeContainer().innerHTML = '<div class="muted">加载财务数据...</div>';
    try{
      const r = await fetch(API_BASE + '/api/finance/transactions');
      const tx = await r.json();
      renderFinance(tx);
    }catch(e){
      activeContainer().innerHTML = '<div class="muted">无法加载财务数据（后端未就绪）。</div>';
    }
  }

  function renderFinance(transactions){
    const wrapper = document.createElement('div');
    const form = document.createElement('form');
    form.className = 'simple-form';
    form.innerHTML = `
      <label>说明 <input name="desc" required></label>
      <label>金额 <input name="amount" type="number" required></label>
      <div><button type="submit">新增流水</button></div>
    `;
    form.addEventListener('submit', async (e)=>{
      e.preventDefault();
      const payload = { desc: form.desc.value, amount: Number(form.amount.value) };
      try{
        const r = await fetch(API_BASE + '/api/finance/transactions', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
        if(!r.ok) throw new Error('提交失败');
        await loadFinance();
      }catch(err){ alert('提交失败：'+err.message); }
    });
    wrapper.appendChild(form);

    const table = document.createElement('table');
    table.style.width = '100%';
    table.innerHTML = `<thead><tr><th>时间</th><th>说明</th><th>金额</th></tr></thead><tbody>${transactions.map(t=>`<tr><td>${t.time||'-'}</td><td>${t.desc}</td><td>${t.amount}</td></tr>`).join('')}</tbody>`;
    wrapper.appendChild(table);
    const cont = activeContainer(); cont.innerHTML = ''; cont.appendChild(wrapper);
  }

  // --- Warnings module ---
  async function loadWarnings(){
    mainTitle.textContent = '预警中心';
    activeContainer().innerHTML = '<div class="muted">加载预警...</div>';
    try{
      const r = await fetch(API_BASE + '/api/warnings/events');
      const items = await r.json();
      renderWarnings(items);
    }catch(e){
      activeContainer().innerHTML = '<div class="muted">无法加载预警数据（后端未就绪）。</div>';
    }
  }

  function renderWarnings(items){
    const wrapper = document.createElement('div');
    const form = document.createElement('form');
    form.className = 'simple-form';
    form.innerHTML = `
      <label>标题 <input name="title" required></label>
      <label>说明 <input name="msg"></label>
      <div><button type="submit">创建告警</button></div>
    `;
    form.addEventListener('submit', async (e)=>{
      e.preventDefault();
      const payload = { title: form.title.value, msg: form.msg.value };
      try{
        const r = await fetch(API_BASE + '/api/warnings/events', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
        if(!r.ok) throw new Error('提交失败');
        await loadWarnings();
      }catch(err){ alert('提交失败：'+err.message); }
    });
    wrapper.appendChild(form);

    const list = document.createElement('div');
    list.style.marginTop = '12px';
    items.forEach(it=>{
      const el = document.createElement('div');
      el.className = 'warning-item';
      el.innerHTML = `<strong>${it.title}</strong> <div class="muted">${it.msg||''}</div>`;
      list.appendChild(el);
    });
    wrapper.appendChild(list);
    const cont = activeContainer(); cont.innerHTML = ''; cont.appendChild(wrapper);
  }

  refresh.addEventListener('click', checkBackend);
  renderModuleList();
  // expose minimal API for inline fallback handlers
  window.village = { openModule, renderModuleList };
  // normalize module ids between index.html and this app (index uses 'user'/'dashboard')
  function normalizeModule(id){
    if(!id) return id;
    if(id === 'user') return 'users';
    if(id === 'dashboard') return 'status';
    return id;
  }

  // bind buttons in static HTML that use data-open="<module>"
  document.querySelectorAll('button[data-open]').forEach(btn=>{
    btn.addEventListener('click', async (e)=>{
      const raw = btn.dataset.open;
      const panel = btn.dataset.panel;
      const target = normalizeModule(raw);
      // highlight left nav if matching
      document.querySelectorAll('.nav-item').forEach(n=> n.classList.remove('active'));
      const navMatch = Array.from(document.querySelectorAll('.nav-item')).find(n=> normalizeModule(n.dataset.module) === target);
      if(navMatch) navMatch.classList.add('active');
      await openModule(target);
      if(panel){
        // only user module currently supports sub-panels
        if(target === 'users') showUserSubpanel(panel);
      }
    });
  });

  // admin-check helper: simple heuristic from users list
  async function isAdmin(){
    try{
      const r = await fetch(API_BASE + '/api/users', {cache:'no-store'});
      if(!r.ok) return false;
      const users = await r.json();
      return users.some(u=> (u.role||'').includes('管理员'));
    }catch(e){ return false; }
  }

  function showUserSubpanel(panel){
    let el;
    if(panel === 'roles') el = renderRoleManagement();
    else if(panel === 'permissions') el = renderPermissionSettings();
    else if(panel === 'residents') { el = renderResidentsPanel(); }
    else if(panel === 'import') el = renderImportPanel();
    else el = renderResidentsPanel();
    openModal(el);
  }

  function renderRoleManagement(){
    const wrapper = document.createElement('div');
    wrapper.innerHTML = `<div class="village-modal-header"><h3>角色管理</h3></div><div class="muted">在此管理系统角色（示例面板）。</div>`;
    const list = document.createElement('div'); list.style.marginTop='12px';
    ['管理员','普通用户','审核员'].forEach(r=>{ const el=document.createElement('div'); el.className='card'; el.style.marginBottom='8px'; el.innerHTML=`<strong>${r}</strong> <div class="muted">描述：${r} 权限集合</div>`; list.appendChild(el); });
    wrapper.appendChild(list);
    return wrapper;
  }

  async function renderPermissionSettings(){
    const ok = await isAdmin();
    const wrapper = document.createElement('div');
    if(!ok){ wrapper.innerHTML = `<div class="muted">操作需要管理员权限。</div>`; return wrapper; }
    wrapper.innerHTML = `<div class="village-modal-header"><h3>权限设置</h3></div><p class="muted">仅管理员可配置权限（示例面板）。</p>`;
    const form = document.createElement('form'); form.className='simple-form';
    form.innerHTML = `
      <label>角色名 <input name="role" required></label>
      <label>权限（逗号分隔） <input name="perms"></label>
      <div><button type="submit" class="btn btn-primary">保存</button></div>
    `;
    form.addEventListener('submit',(e)=>{ e.preventDefault(); alert('已保存（示例）'); });
    wrapper.appendChild(form);
    return wrapper;
  }

  function renderResidentsPanel(){
    const wrapper = document.createElement('div');
    wrapper.innerHTML = `<div class="village-modal-header"><h3>居民列表</h3></div>`;
    const list = document.createElement('div'); list.style.marginTop='12px';
    // placeholder; reuse loadUsers to fetch and render into element
    wrapper.appendChild(list);
    // fetch users and populate
    fetch(API_BASE + '/api/users').then(r=> r.ok? r.json() : Promise.reject()).then(users=>{
      list.innerHTML = '';
      users.forEach(u=>{ const el=document.createElement('div'); el.className='card'; el.style.marginBottom='8px'; el.innerHTML=`<strong>${u.name}</strong> <div class="muted">角色：${u.role||'-'} · ID:${u.id}</div>`; list.appendChild(el); });
    }).catch(()=>{ list.innerHTML = '<div class="muted">无法加载用户数据。</div>'; });
    return wrapper;
  }

  function renderImportPanel(){
    const wrapper = document.createElement('div');
    wrapper.innerHTML = `<div class="village-modal-header"><h3>信息导入</h3></div><p class="muted">上传 CSV 导入居民信息（示例）。</p>`;
    const form = document.createElement('form'); form.className='simple-form';
    form.innerHTML = `<label>选择文件 <input type="file" name="file"></label><div><button class="btn btn-primary" type="submit">上传</button></div>`;
    form.addEventListener('submit',(e)=>{ e.preventDefault(); alert('上传已处理（示例）'); });
    wrapper.appendChild(form);
    return wrapper;
  }

  // modal helpers
  function openModal(contentEl){
    closeModal();
    const overlay = document.createElement('div'); overlay.className='village-modal-overlay'; overlay.id='village-modal-overlay';
    const box = document.createElement('div'); box.className='village-modal';
    const header = document.createElement('div'); header.className='village-modal-header';
    const close = document.createElement('button'); close.className='village-modal-close'; close.textContent='关闭';
    close.addEventListener('click', closeModal);
    header.appendChild(close);
    box.appendChild(header);
    box.appendChild(contentEl);
    overlay.appendChild(box);
    overlay.addEventListener('click', (e)=>{ if(e.target===overlay) closeModal(); });
    document.body.appendChild(overlay);
    document.addEventListener('keydown', escHandler);
  }

  function closeModal(){
    const ex = document.getElementById('village-modal-overlay'); if(ex) ex.remove(); document.removeEventListener('keydown', escHandler);
  }
  function escHandler(e){ if(e.key === 'Escape') closeModal(); }
  // --- Governance (tasks) ---
  let tasks = [
    {id:1, title:'路灯维修', assigned_to:'村务组', status:'进行中', due_date:'2026-02-10'},
    {id:2, title:'疫情防控宣传', assigned_to:'村干部', status:'待处理', due_date:'2026-02-05'}
  ];

  async function loadGovernance(){
    mainTitle.textContent = '基层治理 - 任务面板';
    activeContainer().innerHTML = '<div class="muted">加载任务...</div>';
    renderGovernance();
  }

  function renderGovernance(){
    const wrapper = document.createElement('div');
    const form = document.createElement('form');
    form.className = 'simple-form';
    form.innerHTML = `
      <label>任务标题 <input name="title" required></label>
      <label>指派给 <input name="assigned_to"></label>
      <label>截止日期 <input name="due_date" type="date"></label>
      <div><button type="submit">发布任务</button></div>
    `;
    form.addEventListener('submit', (e)=>{
      e.preventDefault();
      const id = tasks.length ? Math.max(...tasks.map(t=>t.id))+1 : 1;
      tasks.push({id, title:form.title.value, assigned_to:form.assigned_to.value, status:'待处理', due_date:form.due_date.value});
      renderGovernance();
    });
    wrapper.appendChild(form);

    const list = document.createElement('div');
    list.style.marginTop = '12px';
    tasks.forEach(t=>{
      const el = document.createElement('div');
      el.className = 'card';
      el.style.marginBottom = '8px';
      el.innerHTML = `<strong>${t.title}</strong> <div class="muted">${t.assigned_to} · ${t.status} · 截止 ${t.due_date||'-'}</div>`;
      const btn = document.createElement('button'); btn.textContent = '标记完成'; btn.style.marginTop='8px';
      btn.addEventListener('click', ()=>{ t.status='已完成'; renderGovernance(); });
      el.appendChild(btn);
      list.appendChild(el);
    });
    wrapper.appendChild(list);
    const cont = activeContainer(); cont.innerHTML = ''; cont.appendChild(wrapper);
  }

  // --- Feedback ---
  let feedbacks = [ {id:1, source:'APP', content:'道路破损', status:'已受理', created_at:'2026-01-20'} ];
  async function loadFeedback(){
    mainTitle.textContent = '民情反馈';
    activeContainer().innerHTML = '<div class="muted">加载反馈...</div>';
    renderFeedback();
  }
  function renderFeedback(){
    const wrapper = document.createElement('div');
    const form = document.createElement('form'); form.className='simple-form';
    form.innerHTML = `
      <label>来源 <input name="source"></label>
      <label>内容 <input name="content" required></label>
      <div><button type="submit">提交反馈</button></div>
    `;
    form.addEventListener('submit',(e)=>{ e.preventDefault(); const id=feedbacks.length?Math.max(...feedbacks.map(f=>f.id))+1:1; feedbacks.unshift({id, source:form.source.value||'WEB', content:form.content.value, status:'待受理', created_at:new Date().toISOString().split('T')[0]}); renderFeedback(); });
    wrapper.appendChild(form);
    const list=document.createElement('div'); list.style.marginTop='12px';
    feedbacks.forEach(f=>{ const el=document.createElement('div'); el.className='card'; el.style.marginBottom='8px'; el.innerHTML=`<strong>${f.source}</strong> <div class="muted">${f.content} · ${f.status} · ${f.created_at}</div>`; list.appendChild(el); });
    wrapper.appendChild(list); const cont = activeContainer(); cont.innerHTML=''; cont.appendChild(wrapper);
  }

  // --- Industry ---
  async function loadIndustry(){
    mainTitle.textContent = '产业看板';
    activeContainer().innerHTML = '<div class="muted">加载产业指标...</div>';
    renderIndustry();
  }
  function renderIndustry(){
    const wrapper = document.createElement('div');
    wrapper.innerHTML = `<div class="chart-row"><div class="chart-box"><div class="chart-title">基地产量</div><div class="chart-placeholder">示例图表（静态）</div></div><div class="chart-box"><div class="chart-title">指标趋势</div><div class="chart-placeholder">示例图表</div></div></div>`;
    const cont = activeContainer(); cont.innerHTML=''; cont.appendChild(wrapper);
  }

  // --- AI assistant ---
  async function loadAi(){
    mainTitle.textContent = 'AI 助手';
    activeContainer().innerHTML = '<div class="muted">加载 AI 工具...</div>';
    renderAi();
  }
  function renderAi(){
    const wrapper = document.createElement('div');
    const form = document.createElement('form'); form.className='simple-form';
    form.innerHTML = `
      <label>问题或描述 <input name="q" required></label>
      <div><button type="submit">发送到 AI</button></div>
    `;
    form.addEventListener('submit',(e)=>{ e.preventDefault(); const q=form.q.value; const out=document.createElement('div'); out.className='card'; out.style.marginTop='8px'; out.innerHTML=`<strong>AI 回复（示例）</strong><div class="muted">对“${q}”的建议：可参考本地治理手册并联系乡镇办事处。</div>`; wrapper.appendChild(out); });
    wrapper.appendChild(form); const cont = activeContainer(); cont.innerHTML=''; cont.appendChild(wrapper);
  }

  // --- Ops & Audit ---
  async function loadOps(){
    mainTitle.textContent = '运维与审计';
    moduleContent.innerHTML = '<div class="muted">系统健康检查...</div>';
    renderOps();
  }
  function renderOps(){
    const wrapper=document.createElement('div'); wrapper.className='card'; wrapper.innerHTML=`<h3>健康检查</h3><div class="muted">后端/API: ${statusEl.textContent||'未知'}</div>`; moduleContent.innerHTML=''; moduleContent.appendChild(wrapper);
  }
});
