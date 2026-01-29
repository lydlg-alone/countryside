document.addEventListener('DOMContentLoaded', ()=>{
  const statusEl = document.getElementById('status');
  const refresh = document.getElementById('refresh');
  const modulesEl = document.getElementById('modules');
  const moduleContent = document.getElementById('moduleContent');
  const mainTitle = document.getElementById('mainTitle');

  const modules = [
    {id:'status', title:'系统状态'},
    {id:'users', title:'用户管理'},
    {id:'finance', title:'财务管理'},
    {id:'warning', title:'预警中心'},
    {id:'feedback', title:'民情反馈'},
    {id:'ops', title:'运维与审计'},
    {id:'industry', title:'产业看板'},
    {id:'ai', title:'AI 工具'}
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
      const r = await fetch('/api/', {cache:'no-store'});
      const text = await r.text();
      statusEl.textContent = '后端响应：' + text;
      statusEl.style.color = 'green';
    }catch(err){
      statusEl.textContent = '无法连接后端（请确保后端已启动）';
      statusEl.style.color = 'red';
    }
  }

  async function openModule(id){
    mainTitle.textContent = (modules.find(m=>m.id===id)||{}).title || '模块';
    moduleContent.innerHTML = '<div class="muted">加载中...</div>';

    if(id === 'status'){
      await checkBackend();
      moduleContent.innerHTML = '<div class="muted">后端健康检查已完成。使用左侧项切换模块。</div>';
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

    moduleContent.innerHTML = `<h3>${mainTitle.textContent}</h3><p class="muted">此处为 ${mainTitle.textContent} 的示例界面，可进一步实现 CRUD、图表和告警规则配置。</p>`;
  }

  // --- Users module ---
  async function loadUsers(){
    mainTitle.textContent = '用户管理';
    moduleContent.innerHTML = '<div class="muted">加载用户...</div>';
    try{
      const r = await fetch('/api/users', {cache:'no-store'});
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
    moduleContent.innerHTML = '';
    moduleContent.appendChild(wrapper);
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
          const r = await fetch('/api/users/'+user.id, {method:'PUT',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
          if(!r.ok) throw new Error('更新失败');
        }else{
          const r = await fetch('/api/users', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
          if(!r.ok) throw new Error('创建失败');
        }
        await loadUsers();
      }catch(err){
        alert('操作失败：'+err.message);
      }
    });
    form.querySelector('#cancel').addEventListener('click', ()=> loadUsers());
    moduleContent.innerHTML = '';
    moduleContent.appendChild(form);
  }

  async function confirmDeleteUser(id){
    if(!confirm('确认删除用户 ID='+id+' 吗？')) return;
    try{
      const r = await fetch('/api/users/'+id, {method:'DELETE'});
      if(!r.ok) throw new Error('删除失败');
      await loadUsers();
    }catch(err){ alert('删除失败：'+err.message); }
  }

  // --- Finance module ---
  async function loadFinance(){
    mainTitle.textContent = '财务管理';
    moduleContent.innerHTML = '<div class="muted">加载财务数据...</div>';
    try{
      const r = await fetch('/api/finance/transactions');
      const tx = await r.json();
      renderFinance(tx);
    }catch(e){
      moduleContent.innerHTML = '<div class="muted">无法加载财务数据（后端未就绪）。</div>';
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
        const r = await fetch('/api/finance/transactions', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
        if(!r.ok) throw new Error('提交失败');
        await loadFinance();
      }catch(err){ alert('提交失败：'+err.message); }
    });
    wrapper.appendChild(form);

    const table = document.createElement('table');
    table.style.width = '100%';
    table.innerHTML = `<thead><tr><th>时间</th><th>说明</th><th>金额</th></tr></thead><tbody>${transactions.map(t=>`<tr><td>${t.time||'-'}</td><td>${t.desc}</td><td>${t.amount}</td></tr>`).join('')}</tbody>`;
    wrapper.appendChild(table);
    moduleContent.innerHTML = '';
    moduleContent.appendChild(wrapper);
  }

  // --- Warnings module ---
  async function loadWarnings(){
    mainTitle.textContent = '预警中心';
    moduleContent.innerHTML = '<div class="muted">加载预警...</div>';
    try{
      const r = await fetch('/api/warnings/events');
      const items = await r.json();
      renderWarnings(items);
    }catch(e){
      moduleContent.innerHTML = '<div class="muted">无法加载预警数据（后端未就绪）。</div>';
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
        const r = await fetch('/api/warnings/events', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});
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
    moduleContent.innerHTML = '';
    moduleContent.appendChild(wrapper);
  }

  refresh.addEventListener('click', checkBackend);
  renderModuleList();
});
