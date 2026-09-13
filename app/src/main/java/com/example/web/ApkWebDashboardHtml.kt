package com.example.web

object ApkWebDashboardHtml {

  fun getHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>APK Web Hub & Sideload Server</title>
  <style>
    :root {
      --bg: #0b0f19;
      --surface: #131b2e;
      --surface-border: #1e293b;
      --primary: #38bdf8;
      --primary-hover: #0ea5e9;
      --accent: #818cf8;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --success: #34d399;
      --warning: #fbbf24;
      --error: #f87171;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background-color: var(--bg);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    header {
      background: linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%);
      border-bottom: 1px solid var(--surface-border);
      padding: 1rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .logo-group {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .logo-badge {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: linear-gradient(135deg, #38bdf8, #818cf8);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
      font-weight: bold;
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(52, 211, 153, 0.15);
      border: 1px solid rgba(52, 211, 153, 0.3);
      color: var(--success);
      padding: 0.35rem 0.75rem;
      border-radius: 9999px;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--success);
      box-shadow: 0 0 8px var(--success);
    }
    nav {
      background: var(--surface);
      border-bottom: 1px solid var(--surface-border);
      display: flex;
      overflow-x: auto;
      padding: 0 1rem;
    }
    .nav-btn {
      background: none;
      border: none;
      color: var(--text-muted);
      padding: 0.9rem 1.25rem;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      border-bottom: 2px solid transparent;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .nav-btn:hover { color: var(--text); }
    .nav-btn.active {
      color: var(--primary);
      border-bottom-color: var(--primary);
    }
    main {
      flex: 1;
      max-width: 1100px;
      width: 100%;
      margin: 0 auto;
      padding: 1.5rem 1rem;
    }
    .tab-content { display: none; }
    .tab-content.active { display: block; }
    .card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 14px;
      padding: 1.5rem;
      margin-bottom: 1.5rem;
      box-shadow: 0 10px 25px -5px rgba(0,0,0,0.3);
    }
    h2 { font-size: 1.3rem; margin-bottom: 0.5rem; }
    p.subtitle { color: var(--text-muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .dropzone {
      border: 2px dashed #334155;
      border-radius: 12px;
      padding: 2.5rem 1.5rem;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
      background: rgba(15, 23, 42, 0.5);
    }
    .dropzone:hover, .dropzone.dragover {
      border-color: var(--primary);
      background: rgba(56, 189, 248, 0.05);
    }
    .drop-icon { font-size: 3rem; margin-bottom: 0.5rem; }
    .btn {
      background: var(--primary);
      color: #0b0f19;
      border: none;
      padding: 0.75rem 1.5rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.95rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      transition: background 0.2s;
    }
    .btn:hover { background: var(--primary-hover); }
    .btn-secondary {
      background: #1e293b;
      color: var(--text);
    }
    .btn-secondary:hover { background: #334155; }
    .btn-outline {
      background: transparent;
      border: 1px solid #334155;
      color: var(--text);
    }
    .btn-outline:hover { background: #1e293b; border-color: var(--primary); }
    .progress-bar-wrap {
      width: 100%;
      height: 8px;
      background: #1e293b;
      border-radius: 4px;
      margin: 1rem 0;
      overflow: hidden;
      display: none;
    }
    .progress-bar-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, var(--primary), var(--accent));
      transition: width 0.2s;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1rem;
    }
    .stat-item {
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid var(--surface-border);
      border-radius: 10px;
      padding: 1rem;
    }
    .stat-label { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
    .stat-value { font-size: 1.1rem; font-weight: 600; margin-top: 0.25rem; word-break: break-all; }
    .input-field {
      width: 100%;
      padding: 0.75rem 1rem;
      background: #0f172a;
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      color: var(--text);
      font-size: 0.95rem;
      margin-bottom: 0.75rem;
    }
    .input-field:focus { outline: none; border-color: var(--primary); }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th { text-align: left; padding: 0.75rem; font-size: 0.8rem; color: var(--text-muted); border-bottom: 1px solid var(--surface-border); }
    td { padding: 0.75rem; font-size: 0.9rem; border-bottom: 1px solid rgba(255,255,255,0.05); }
    tr:hover td { background: rgba(255,255,255,0.02); }
    .badge {
      display: inline-block;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .badge-primary { background: rgba(56, 189, 248, 0.2); color: var(--primary); }
    .badge-system { background: rgba(148, 163, 184, 0.2); color: var(--text-muted); }
    .perm-list { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.75rem; }
    .perm-chip {
      background: #1e293b;
      font-size: 0.75rem;
      padding: 0.25rem 0.6rem;
      border-radius: 6px;
      font-family: monospace;
    }
    .preset-card {
      background: #0f172a;
      border: 1px solid var(--surface-border);
      border-radius: 10px;
      padding: 1rem;
      cursor: pointer;
      transition: all 0.2s;
    }
    .preset-card:hover {
      border-color: var(--primary);
      transform: translateY(-2px);
    }
    #toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      background: #1e293b;
      border: 1px solid var(--primary);
      color: var(--text);
      padding: 0.75rem 1.25rem;
      border-radius: 8px;
      box-shadow: 0 10px 25px rgba(0,0,0,0.5);
      display: none;
      z-index: 100;
      font-size: 0.9rem;
    }
  </style>
</head>
<body>
  <header>
    <div class="logo-group">
      <div class="logo-badge">⚡</div>
      <div>
        <h1 style="font-size: 1.2rem; font-weight: bold;">APK Web Hub</h1>
        <div style="font-size: 0.75rem; color: var(--text-muted);">Android Sideload & Backup Gateway</div>
      </div>
    </div>
    <div style="display: flex; align-items: center; gap: 0.75rem;">
      <div class="status-pill">
        <span class="status-dot"></span>
        <span id="device-status-text">Connected to Android Device</span>
      </div>
    </div>
  </header>

  <nav>
    <button class="nav-btn active" onclick="switchTab('upload')">📦 Upload & Sideload</button>
    <button class="nav-btn" onclick="switchTab('apps')">📱 Installed Apps & Backup</button>
    <button class="nav-btn" onclick="switchTab('downloader')">🌐 Web APK Downloader</button>
    <button class="nav-btn" onclick="switchTab('telemetry')">⚙️ Device Telemetry</button>
  </nav>

  <main>
    <!-- TAB 1: UPLOAD & SIDELOAD -->
    <div id="tab-upload" class="tab-content active">
      <div class="card">
        <h2>Drop & Sideload APK</h2>
        <p class="subtitle">Upload any .apk file from your computer or phone to inspect and trigger direct installation.</p>
        
        <input type="file" id="file-input" accept=".apk" style="display:none" onchange="handleFileSelect(event)">
        <div class="dropzone" id="dropzone" onclick="document.getElementById('file-input').click()">
          <div class="drop-icon">📤</div>
          <h3 style="font-size: 1.1rem; margin-bottom: 0.25rem;">Click to select or drag & drop an APK here</h3>
          <p style="color: var(--text-muted); font-size: 0.85rem;">Standard Android Application Package (.apk)</p>
        </div>

        <div class="progress-bar-wrap" id="upload-progress-wrap">
          <div class="progress-bar-fill" id="upload-progress-fill"></div>
        </div>
        <div id="upload-status" style="font-size: 0.85rem; color: var(--text-muted); margin-top: 0.5rem; text-align: center;"></div>
      </div>

      <!-- Live Analysis Card -->
      <div class="card" id="apk-analysis-card" style="display: none;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.25rem;">
          <div>
            <span class="badge badge-primary" id="apk-status-badge">Package Verified</span>
            <h2 id="apk-app-name" style="margin-top: 0.5rem; font-size: 1.4rem;">App Name</h2>
            <div id="apk-package-name" style="font-family: monospace; color: var(--text-muted); font-size: 0.85rem;">com.example.app</div>
          </div>
          <button class="btn" onclick="triggerDeviceInstall()" id="install-btn">
            🚀 Install on Android Device
          </button>
        </div>

        <div class="grid-2">
          <div class="stat-item">
            <div class="stat-label">Version</div>
            <div class="stat-value" id="apk-version">v1.0.0 (1)</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Package File Size</div>
            <div class="stat-value" id="apk-size">0 MB</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Target Android SDK</div>
            <div class="stat-value" id="apk-target-sdk">API 34</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Minimum Android SDK</div>
            <div class="stat-value" id="apk-min-sdk">API 24</div>
          </div>
        </div>

        <div style="margin-top: 1.5rem;">
          <h3 style="font-size: 0.95rem; margin-bottom: 0.5rem;">Requested Permissions</h3>
          <div class="perm-list" id="apk-permissions-list"></div>
        </div>
      </div>
    </div>

    <!-- TAB 2: INSTALLED APPS & BACKUP -->
    <div id="tab-apps" class="tab-content">
      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem;">
          <div>
            <h2>Device Installed Applications</h2>
            <p class="subtitle" style="margin-bottom: 0;">Extract and backup installed application APKs directly to your computer.</p>
          </div>
          <button class="btn btn-secondary" onclick="loadInstalledApps()">🔄 Refresh List</button>
        </div>

        <input type="text" id="app-search-input" class="input-field" placeholder="Search applications by name or package..." oninput="filterApps()">

        <div style="overflow-x: auto;">
          <table>
            <thead>
              <tr>
                <th>App Name</th>
                <th>Package ID</th>
                <th>Version</th>
                <th>Size</th>
                <th>Type</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody id="apps-table-body">
              <tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Loading installed packages...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- TAB 3: WEB APK DOWNLOADER -->
    <div id="tab-downloader" class="tab-content">
      <div class="card">
        <h2>Remote Web APK Downloader</h2>
        <p class="subtitle">Directly fetch and sideload APK packages from any public URL or trusted open-source repository.</p>
        
        <label style="font-size: 0.85rem; font-weight: 600; display: block; margin-bottom: 0.4rem;">Direct APK Download URL</label>
        <div style="display: flex; gap: 0.5rem; margin-bottom: 1.5rem;">
          <input type="url" id="remote-url-input" class="input-field" style="margin-bottom: 0;" placeholder="https://example.com/app-release.apk">
          <button class="btn" onclick="downloadRemoteUrl()">Download & Install</button>
        </div>

        <h3 style="font-size: 1rem; margin-bottom: 0.75rem;">Verified Open-Source Presets</h3>
        <div class="grid-2">
          <div class="preset-card" onclick="setRemoteUrl('https://f-droid.org/F-Droid.apk')">
            <div style="font-weight: bold;">F-Droid Client</div>
            <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem;">Free and Open Source Android app repository</div>
          </div>
          <div class="preset-card" onclick="setRemoteUrl('https://archive.newpipe.net/fdroid/repo/NewPipe_v0.27.2.apk')">
            <div style="font-weight: bold;">NewPipe Media Player</div>
            <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem;">Lightweight privacy-friendly YouTube frontend</div>
          </div>
          <div class="preset-card" onclick="setRemoteUrl('https://get.videolan.org/vlc-android/3.5.4/VLC-Android-3.5.4-arm64-v8a.apk')">
            <div style="font-weight: bold;">VLC Media Player</div>
            <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem;">Official open-source audio & video player for Android</div>
          </div>
          <div class="preset-card" onclick="setRemoteUrl('https://updates.signal.org/android/Signal-Android-website-prod-universal-release-7.15.3.apk')">
            <div style="font-weight: bold;">Signal Messenger</div>
            <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem;">Official standalone direct release for Signal</div>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 4: TELEMETRY -->
    <div id="tab-telemetry" class="tab-content">
      <div class="card">
        <h2>Device & Sideload Status</h2>
        <p class="subtitle">Real-time status of the Android runtime and package installer engine.</p>
        
        <div class="grid-2">
          <div class="stat-item">
            <div class="stat-label">Device Model</div>
            <div class="stat-value" id="device-model">-</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Android OS Version</div>
            <div class="stat-value" id="device-os">-</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Unknown App Sideloading</div>
            <div class="stat-value" id="device-install-perm">-</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">Installed Apps Count</div>
            <div class="stat-value" id="device-app-count">-</div>
          </div>
        </div>

        <div style="margin-top: 1.5rem; display: flex; gap: 0.75rem;">
          <button class="btn btn-secondary" onclick="loadTelemetry()">Refresh Status</button>
        </div>
      </div>
    </div>
  </main>

  <div id="toast"></div>

  <script>
    let allInstalledApps = [];
    let currentUploadedFilePath = "";

    function showToast(msg) {
      const t = document.getElementById('toast');
      t.innerText = msg;
      t.style.display = 'block';
      setTimeout(() => { t.style.display = 'none'; }, 3500);
    }

    function switchTab(name) {
      document.querySelectorAll('.nav-btn').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
      
      const tabElement = document.getElementById('tab-' + name);
      if (tabElement) tabElement.classList.add('active');
      
      // Update active nav button
      const navButtons = document.querySelectorAll('.nav-btn');
      navButtons.forEach(btn => {
        if (btn.getAttribute('onclick').includes(name)) {
          btn.classList.add('active');
        }
      });

      if (name === 'apps' && allInstalledApps.length === 0) {
        loadInstalledApps();
      } else if (name === 'telemetry') {
        loadTelemetry();
      }
    }

    // Drag & Drop Setup
    const dropzone = document.getElementById('dropzone');
    dropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dropzone.classList.add('dragover');
    });
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
    dropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        uploadFile(e.dataTransfer.files[0]);
      }
    });

    function handleFileSelect(event) {
      if (event.target.files && event.target.files.length > 0) {
        uploadFile(event.target.files[0]);
      }
    }

    function uploadFile(file) {
      if (!file.name.toLowerCase().endsWith('.apk')) {
        showToast('Please select a valid .apk file');
        return;
      }

      const progressWrap = document.getElementById('upload-progress-wrap');
      const progressFill = document.getElementById('upload-progress-fill');
      const uploadStatus = document.getElementById('upload-status');

      progressWrap.style.display = 'block';
      progressFill.style.width = '0%';
      uploadStatus.innerText = 'Uploading ' + file.name + '...';

      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload?filename=' + encodeURIComponent(file.name), true);

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const percent = Math.round((e.loaded / e.total) * 100);
          progressFill.style.width = percent + '%';
          uploadStatus.innerText = 'Uploading: ' + percent + '% (' + (e.loaded / (1024*1024)).toFixed(1) + ' MB)';
        }
      };

      xhr.onload = () => {
        progressWrap.style.display = 'none';
        if (xhr.status === 200) {
          try {
            const data = JSON.parse(xhr.responseText);
            displayApkDetails(data);
            showToast('APK parsed successfully!');
          } catch(e) {
            uploadStatus.innerText = 'Upload complete, but failed to parse response.';
          }
        } else {
          uploadStatus.innerText = 'Upload failed: ' + xhr.responseText;
        }
      };

      xhr.onerror = () => {
        progressWrap.style.display = 'none';
        uploadStatus.innerText = 'Network error while uploading.';
      };

      xhr.send(file);
    }

    function displayApkDetails(apk) {
      currentUploadedFilePath = apk.cachedFilePath || "";
      document.getElementById('apk-analysis-card').style.display = 'block';
      document.getElementById('apk-app-name').innerText = apk.appName || apk.packageName;
      document.getElementById('apk-package-name').innerText = apk.packageName;
      document.getElementById('apk-version').innerText = 'v' + apk.versionName + ' (' + apk.versionCode + ')';
      document.getElementById('apk-size').innerText = apk.formattedSize;
      document.getElementById('apk-target-sdk').innerText = 'API ' + apk.targetSdkVersion;
      document.getElementById('apk-min-sdk').innerText = 'API ' + apk.minSdkVersion;

      const permContainer = document.getElementById('apk-permissions-list');
      permContainer.innerHTML = '';
      if (apk.permissions && apk.permissions.length > 0) {
        apk.permissions.forEach(p => {
          const chip = document.createElement('span');
          chip.className = 'perm-chip';
          chip.innerText = p.split('.').pop();
          chip.title = p;
          permContainer.appendChild(chip);
        });
      } else {
        permContainer.innerHTML = '<span style="color: var(--text-muted); font-size: 0.85rem;">No permissions requested</span>';
      }
    }

    function triggerDeviceInstall() {
      fetch('/api/install', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ filePath: currentUploadedFilePath })
      })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          showToast('Triggered native installer on device!');
        } else {
          showToast('Error: ' + (data.error || 'Failed to launch installer'));
        }
      })
      .catch(err => showToast('Error contacting device installer.'));
    }

    function loadInstalledApps() {
      const tbody = document.getElementById('apps-table-body');
      tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Loading packages from phone...</td></tr>';

      fetch('/api/apps')
        .then(res => res.json())
        .then(apps => {
          allInstalledApps = apps;
          renderAppsTable(apps);
        })
        .catch(err => {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--error);">Failed to load packages.</td></tr>';
        });
    }

    function renderAppsTable(apps) {
      const tbody = document.getElementById('apps-table-body');
      if (!apps || apps.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">No applications found.</td></tr>';
        return;
      }
      let html = '';
      for (let i = 0; i < apps.length; i++) {
        const app = apps[i];
        const badge = app.isSystemApp ? '<span class="badge badge-system">System</span>' : '<span class="badge badge-primary">User</span>';
        html += '<tr>' +
          '<td><strong>' + escapeHtml(app.appName) + '</strong></td>' +
          '<td style="font-family: monospace; font-size: 0.8rem; color: var(--text-muted);">' + escapeHtml(app.packageName) + '</td>' +
          '<td>v' + escapeHtml(app.versionName) + '</td>' +
          '<td>' + escapeHtml(app.formattedSize) + '</td>' +
          '<td>' + badge + '</td>' +
          '<td><a href="/api/download-apk?package=' + encodeURIComponent(app.packageName) + '" class="btn btn-outline" style="padding: 0.35rem 0.75rem; font-size: 0.8rem; text-decoration: none;">💾 Download APK</a></td>' +
          '</tr>';
      }
      tbody.innerHTML = html;
    }

    function filterApps() {
      const q = document.getElementById('app-search-input').value.toLowerCase().trim();
      if (!q) {
        renderAppsTable(allInstalledApps);
        return;
      }
      const filtered = allInstalledApps.filter(a => 
        a.appName.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
      );
      renderAppsTable(filtered);
    }

    function setRemoteUrl(url) {
      document.getElementById('remote-url-input').value = url;
    }

    function downloadRemoteUrl() {
      const url = document.getElementById('remote-url-input').value.trim();
      if (!url) {
        showToast('Please enter an APK URL');
        return;
      }
      showToast('Downloading remote APK to device...');
      fetch('/api/download-url', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url })
      })
      .then(res => res.json())
      .then(data => {
        if (data.success && data.apk) {
          showToast('Downloaded and verified!');
          switchTab('upload');
          displayApkDetails(data.apk);
        } else {
          showToast('Failed: ' + (data.error || 'Download error'));
        }
      })
      .catch(e => showToast('Failed to trigger download.'));
    }

    function loadTelemetry() {
      fetch('/api/status')
        .then(res => res.json())
        .then(data => {
          document.getElementById('device-model').innerText = data.deviceModel || '-';
          document.getElementById('device-os').innerText = 'Android ' + data.osVersion + ' (API ' + data.sdkLevel + ')';
          document.getElementById('device-install-perm').innerText = data.canInstallPackages ? 'Allowed ✓' : 'Permission Required ✗';
          document.getElementById('device-app-count').innerText = data.installedAppCount + ' packages';
        })
        .catch(() => showToast('Failed to fetch telemetry.'));
    }

    function escapeHtml(str) {
      if (!str) return '';
      return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Initial check
    loadTelemetry();
  </script>
</body>
</html>
    """.trimIndent()
  }
}
