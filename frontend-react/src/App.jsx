import { useState } from 'react';
import {
  Bell,
  Check,
  Copy,
  ChevronRight,
  FileText,
  Home,
  Laptop,
  MapPin,
  MoreHorizontal,
  Plus,
  Refrigerator,
  ScanLine,
  Search,
  Smartphone,
  Trash2,
  Vault,
  X,
} from 'lucide-react';

const demo = [
  {
    name: 'MacBook Pro 14-inch',
    brand: 'Apple - M3 Pro',
    serial: 'C02XQ1ABJGH5',
    room: 'Office',
    date: '2025-02-14',
    price: '199900',
    Icon: Laptop,
    parts: [['Main Unit', 12, 'STANDARD'], ['Battery', 12, 'PRORATED']],
  },
  {
    name: 'Samsung Family Hub',
    brand: 'Samsung - RF85A',
    serial: '0B7K29XW400123',
    room: 'Kitchen',
    date: '2024-08-22',
    price: '148500',
    Icon: Refrigerator,
    parts: [['Main Unit', 24, 'STANDARD'], ['Compressor', 120, 'STANDARD']],
  },
  {
    name: 'iPhone 16 Pro',
    brand: 'Apple - 256GB',
    serial: 'F2LXQ09VM8',
    room: 'Bedroom',
    date: '2025-01-08',
    price: '134900',
    Icon: Smartphone,
    parts: [['Main Unit', 12, 'STANDARD']],
  },
  {
    name: 'Sony WH-1000XM5 Headphones',
    brand: 'Sony - Wireless Headphones',
    serial: 'SONY-WH1000XM5-DEMO',
    room: 'Office',
    date: (() => {
      const expiry = new Date();
      expiry.setHours(0, 0, 0, 0);
      expiry.setDate(expiry.getDate() + 2);
      expiry.setMonth(expiry.getMonth() - 12);
      return [expiry.getFullYear(), String(expiry.getMonth() + 1).padStart(2, '0'), String(expiry.getDate()).padStart(2, '0')].join('-');
    })(),
    price: '29900',
    Icon: Smartphone,
    parts: [['Main Unit', 12, 'STANDARD']],
  },
];

const emptyForm = () => ({
  name: '',
  brand: '',
  serial: '',
  room: '',
  date: new Date().toISOString().slice(0, 10),
  price: '',
  gstin: '',
  parts: [['Main Unit', 12, 'STANDARD']],
});

function classifyReminderTier(remainingDays) {
  if (remainingDays < 0) return 'EXPIRED_ARCHIVED_NO_ALERTS';
  if (remainingDays > 30) return 'SILENT';
  if (remainingDays >= 15) return 'TIER_1_ADVISORY';
  if (remainingDays >= 4) return 'TIER_2_HIGH_PRIORITY';
  if (remainingDays <= 3) return 'TIER_3_CRITICAL';
  return 'SILENT';
}

export default function App() {
  const [assets, setAssets] = useState(demo);
  const [documents, setDocuments] = useState([]);
  const [activeTab, setActiveTab] = useState('Assets');
  const [mode, setMode] = useState(false);
  const [query, setQuery] = useState('');
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState('');

  const patch = (key, value) => setForm({ ...form, [key]: value });

  async function scan(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    const data = new FormData();
    data.append('file', file);
    try {
      const response = await fetch('/api/v1/receipts/scan', { method: 'POST', body: data });
      if (!response.ok) throw Error('Receipt scan failed');
      const result = await response.json();
      const fields = result.fields;
      const warrantyParts = Array.isArray(fields.warranty_components)
        ? fields.warranty_components.map((part) => [
          part.name || 'Main Unit',
          part.duration_months || 12,
          part.warranty_type || 'STANDARD',
        ])
        : [];
      setForm({
        ...emptyForm(),
        name: fields.product_title || '',
        serial: fields.serial_number || '',
        date: /^\d{4}-\d{2}-\d{2}$/.test(fields.purchase_date || '')
          ? fields.purchase_date
          : new Date().toISOString().slice(0, 10),
        price: fields.purchase_price ?? '',
        gstin: fields.gstin || '',
        parts: warrantyParts.length ? warrantyParts : [['Main Unit', 12, 'STANDARD']],
        hash: result.sha256_hash,
      });
      setMsg('Receipt scanned. Review and confirm the extracted details.');
    } catch {
      setMsg('OCR unavailable; enter the asset details manually.');
      setForm({ ...emptyForm(), name: 'New asset' });
    }
  }

  function save(event) {
    event.preventDefault();
    const savedAsset = { ...form, Icon: Smartphone };
    setAssets([savedAsset, ...assets]);
    if (form.hash) {
      setDocuments((current) => [{
        id: `${form.hash}-${Date.now()}`,
        assetName: form.name,
        documentType: 'Tax Invoice',
        serial: form.serial,
        uploadDate: new Date().toISOString().slice(0, 10),
        hash: form.hash,
      }, ...current]);
    }
    setForm(null);
    setMsg('Asset added to your vault.');
  }

  async function copyHash(hash) {
    try {
      await navigator.clipboard.writeText(hash);
      setMsg('SHA-256 hash copied to clipboard.');
    } catch {
      setMsg('Unable to copy the hash. Select and copy it from the table.');
    }
  }

  async function downloadDossier(asset) {
    try {
      const today = new Date();
      const components = asset.parts.map(([name, months, type]) => {
        const expiry = new Date(asset.date);
        expiry.setMonth(expiry.getMonth() + Number(months));
        const remainingDays = Math.ceil((expiry - today) / 86400000);
        const status = remainingDays < 0
          ? 'Expired'
          : remainingDays < 31
            ? 'Expiring soon'
            : 'Active';
        const reminderTier = classifyReminderTier(remainingDays);
        const expiryDate = [
          expiry.getFullYear(),
          String(expiry.getMonth() + 1).padStart(2, '0'),
          String(expiry.getDate()).padStart(2, '0'),
        ].join('-');

        return {
          name,
          type,
          status,
          expiry_date: expiryDate,
          remaining_days: remainingDays,
          reminder_tier: reminderTier,
        };
      });
      const response = await fetch(`http://${window.location.hostname}:8080/api/dossier`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...asset,
          asset_name: asset.name,
          serial_number: asset.serial,
          sha256_hash: asset.hash || '',
          components,
        }),
      });
      if (!response.ok) throw Error('Dossier request failed');

      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      const serial = String(asset.serial || 'asset').replace(/[<>:"/\\|?*\x00-\x1f]/g, '-');
      link.href = objectUrl;
      link.download = `${serial}-dossier.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch {
      setMsg(`Unable to download the dossier for ${asset.name}.`);
    }
  }

  function partEdit(index, key, value) {
    patch('parts', form.parts.map((part, partIndex) =>
      partIndex === index ? part.map((item, itemIndex) => itemIndex === key ? value : item) : part));
  }

  function coverage(asset, part) {
    const { end, remainingDays: left, status } = getCoverage(asset, part);
    const start = new Date(asset.date);
    const percent = Math.min(100, Math.max(0, (end - new Date()) / (end - start) * 100));

    return (
      <div className="coverage" key={part[0]}>
        <div className="line">
          <b>{part[0]} <small>{part[2] === 'PRORATED' ? 'Prorated' : ''}</small></b>
          <span className={status === 'Active' ? 'green' : status === 'Expired' ? 'red' : 'amber'}>{status}</span>
        </div>
        <div className="bar"><i style={{ width: `${percent}%` }} /></div>
        <div className="line faint">
          <span>{Math.max(0, left)} days left</span>
          <span>Until {end.toLocaleDateString('en-IN')}</span>
        </div>
      </div>
    );
  }

  function getCoverage(asset, part) {
    const end = new Date(asset.date);
    end.setMonth(end.getMonth() + Number(part[1]));
    const remainingDays = Math.ceil((end - new Date()) / 86400000);
    const status = remainingDays < 0 ? 'Expired' : remainingDays < 31 ? 'Expiring Soon' : 'Active';
    const reminderTier = classifyReminderTier(remainingDays);
    return { end, remainingDays, status, reminderTier };
  }

  const alerts = assets.flatMap((asset) => asset.parts
    .map((part) => ({ asset, part, ...getCoverage(asset, part) }))
    .filter((item) => item.status === 'Expired' || item.status === 'Expiring Soon'));
  const activeAlertCount = alerts.filter((item) => item.remainingDays >= 0).length;

  const visibleAssets = assets.filter((asset) =>
    `${asset.name} ${asset.room} ${asset.serial}`.toLowerCase().includes(query.toLowerCase()));

  return (
    <div className="layout">
      <aside>
        <div className="logo"><Vault size={18} aria-hidden="true" /> <b>everkeep</b></div>
        <div className="home"><Home size={14} aria-hidden="true" /> Mehta Household</div>
        <small>WORKSPACE</small>
        <nav>
          <button aria-current={activeTab === 'Assets' ? 'page' : undefined} className={activeTab === 'Assets' ? 'active' : ''} onClick={() => setActiveTab('Assets')}>
            <Vault size={14} aria-hidden="true" /> Assets <span>{assets.length}</span>
          </button>
          <button aria-current={activeTab === 'Documents' ? 'page' : undefined} className={activeTab === 'Documents' ? 'active' : ''} onClick={() => setActiveTab('Documents')}>
            <FileText size={14} aria-hidden="true" /> Documents <span>{documents.length}</span>
          </button>
          <button aria-current={activeTab === 'Warranty alerts' ? 'page' : undefined} className={activeTab === 'Warranty alerts' ? 'active' : ''} onClick={() => setActiveTab('Warranty alerts')}>
            <Bell size={14} aria-hidden="true" /> Warranty alerts <span>{activeAlertCount}</span>
          </button>
        </nav>
      <div className="user">AM - Arjun Mehta<br /><small>Personal workspace</small></div>
      </aside>

      <main>
        <header>
          <div>
            <small>Workspace <ChevronRight size={11} aria-hidden="true" /> {activeTab}</small>
            <h1>{activeTab === 'Assets' ? 'Your assets' : activeTab} <sup>{activeTab === 'Assets' ? assets.length : activeTab === 'Documents' ? documents.length : activeAlertCount}</sup></h1>
            <p>{activeTab === 'Assets'
              ? 'Keep every purchase, receipt, and warranty in one place.'
              : activeTab === 'Documents'
                ? 'Receipts and warranty proofs stored with their verification hashes.'
                : 'Coverage that has expired or is nearing its expiry date.'}</p>
          </div>
          {activeTab === 'Assets' && <button className="primary" onClick={() => setForm(emptyForm())}>
            <Plus size={14} aria-hidden="true" /> Add asset
          </button>}
        </header>

        <section className="stats">
          {[
            ['TOTAL ASSETS', assets.length],
            ['ACTIVE COVERAGE', assets.reduce((count, asset) => count + asset.parts.length, 0)],
            ['EXPIRING SOON', alerts.filter((alert) => alert.status === 'Expiring Soon').length],
            ['DOCUMENTS', documents.length],
          ].map(([label, count]) => <div key={label}><small>{label}</small><b>{count}</b></div>)}
        </section>

        {activeTab === 'Assets' && <section className="tools">
          <label className="search"><Search size={14} aria-hidden="true" />
            <input placeholder="Search assets, rooms, serial numbers..." value={query} onChange={(event) => setQuery(event.target.value)} />
          </label>
          <div className="modeToggle" role="group" aria-label="Workspace mode">
            <button className={!mode ? 'selected' : ''} onClick={() => setMode(false)}>Home</button>
            <button className={mode ? 'selected' : ''} onClick={() => setMode(true)}>Technician / Resale</button>
          </div>
        </section>}

        {msg && <div className="message">{msg}<button aria-label="Dismiss message" onClick={() => setMsg('')}><X size={14} /></button></div>}

        {activeTab === 'Assets' && <section className="grid">
          {visibleAssets.map((asset, index) => {
            const AssetIcon = asset.Icon || Smartphone;
            return (
              <article key={asset.serial + index}>
                <div className="assetHead">
                  <div className="assetIcon"><AssetIcon size={21} aria-hidden="true" /></div>
                  <div><h2>{asset.name}</h2><small>{asset.brand}</small></div>
                  <button className="iconButton" aria-label={`More options for ${asset.name}`}><MoreHorizontal size={17} /></button>
                </div>
                <div className="meta">
                  <span><MapPin size={12} aria-hidden="true" /> {asset.room || 'Room not set'}</span>
                  <span>Purchased {new Date(asset.date).toLocaleDateString('en-IN')}</span>
                </div>
                <div className="serial"><small>SERIAL NUMBER</small><b>{asset.serial || 'Not recorded'}</b></div>
                {mode && <div className="resale">
                  <span>INR {asset.price || '0'} purchase value</span>
                  <button onClick={() => downloadDossier(asset)}>Download dossier</button>
                </div>}
                <div className="label">WARRANTY COVERAGE <span>{asset.parts.length} parts</span></div>
                {asset.parts.map((part) => coverage(asset, part))}
                <button className="link" onClick={() => setMsg(`Asset details: ${asset.name}`)}>View asset details <ChevronRight size={12} /></button>
              </article>
            );
          })}
          <label className="upload">
            <input type="file" accept="image/*" onChange={scan} />
            <ScanLine size={22} aria-hidden="true" />
            <b>Scan a receipt</b>
            <span>Drop a receipt image or browse files</span>
            <small>PNG, JPG up to 10MB</small>
          </label>
        </section>}

        {activeTab === 'Documents' && <section className="documentsPanel">
          {documents.length === 0 ? <div className="emptyState">
            <FileText size={24} aria-hidden="true" />
            <b>No documents yet</b>
            <span>Scan and save a receipt to add it to your documents.</span>
          </div> : <div className="tableScroll"><table className="documentsTable">
            <thead><tr>
              <th>Asset Name</th><th>Document Type</th><th>Serial Number</th>
              <th>Upload Date</th><th>SHA-256 Verification Hash</th><th></th>
            </tr></thead>
            <tbody>{documents.map((document) => <tr key={document.id}>
              <td>{document.assetName}</td>
              <td>{document.documentType}</td>
              <td>{document.serial || 'Not recorded'}</td>
              <td>{new Date(`${document.uploadDate}T00:00:00`).toLocaleDateString('en-IN')}</td>
              <td className="hashCell">{document.hash}</td>
              <td><button className="copyHash" onClick={() => copyHash(document.hash)}><Copy size={13} /> Copy Hash</button></td>
            </tr>)}</tbody>
          </table></div>}
        </section>}

        {activeTab === 'Warranty alerts' && <section className="alertsPanel">
          {alerts.length === 0 ? <div className="emptyState">
            <Check size={24} aria-hidden="true" />
            <b>No warranty alerts</b>
            <span>All component warranties are active.</span>
          </div> : <div className="tableScroll"><table className="alertsTable">
            <thead><tr><th>Asset</th><th>Component</th><th>Status</th><th>Expiry date</th><th>Days</th><th>Reminder tier</th><th></th></tr></thead>
            <tbody>{alerts.map(({ asset, part, end, remainingDays, reminderTier }, index) => <tr key={`${asset.serial}-${part[0]}-${index}`}>
              <td>{asset.name}</td>
              <td>{part[0]}</td>
              <td><span className={`alertStatus ${remainingDays < 0 ? 'archived' : 'amber'}`}>{remainingDays < 0 ? 'Archived' : 'Expiring Soon'}</span></td>
              <td>{end.toLocaleDateString('en-IN')}</td>
              <td>{remainingDays < 0 ? `${Math.abs(remainingDays)} days overdue` : `${remainingDays} days remaining`}</td>
              <td><span className={`tierBadge ${reminderTier.toLowerCase()}`}>
                {reminderTier === 'EXPIRED_ARCHIVED_NO_ALERTS'
                  ? 'EXPIRED (ARCHIVED - NO ALERTS)'
                  : reminderTier.replaceAll('_', ' ')}
              </span></td>
              <td><button className="downloadDossier" onClick={() => downloadDossier(asset)}>Download Dossier</button></td>
            </tr>)}</tbody>
          </table></div>}
        </section>}

        {form && <div className="shade" onClick={(event) => event.target === event.currentTarget && setForm(null)}>
          <form onSubmit={save}>
            <div className="formHead">
              <div><small>NEW PURCHASE</small><h2>Review &amp; Confirm Extracted Details</h2></div>
              <button className="iconButton" type="button" aria-label="Close form" onClick={() => setForm(null)}><X size={16} /></button>
            </div>
            <div className="fields">
              {[
                ['Asset name', 'name'], ['Brand & model', 'brand'], ['Serial / IMEI', 'serial'],
                ['Room tag', 'room'], ['Purchase date', 'date'], ['Purchase price (INR)', 'price'], ['GSTIN', 'gstin'],
              ].map(([label, key]) => <label key={key}>{label}
                <input required={key === 'name'} type={key === 'date' ? 'date' : 'text'} value={form[key]} onChange={(event) => patch(key, event.target.value)} />
              </label>)}
            </div>
            <div className="partHead">
              <b>Warranty components</b>
              <button type="button" onClick={() => patch('parts', [...form.parts, ['', 12, 'STANDARD']])}><Plus size={13} /> Add part</button>
            </div>
            {form.parts.map((part, index) => <div className="part" key={index}>
              <input placeholder="Part name" value={part[0]} onChange={(event) => partEdit(index, 0, event.target.value)} />
              <input type="number" min="0" value={part[1]} onChange={(event) => partEdit(index, 1, event.target.value)} />
              <span>months</span>
              <select value={part[2]} onChange={(event) => partEdit(index, 2, event.target.value)}><option>STANDARD</option><option>PRORATED</option></select>
              <button type="button" aria-label="Remove warranty component" onClick={() => patch('parts', form.parts.filter((_, partIndex) => index !== partIndex))}><Trash2 size={13} /></button>
            </div>)}
            {form.hash && <div className="hash"><Check size={13} /> Receipt SHA-256 {form.hash.slice(0, 20)}...</div>}
            <div className="actions">
              <button type="button" onClick={() => setForm(null)}>Cancel</button>
              <button className="primary"><Check size={14} /> Save asset</button>
            </div>
          </form>
        </div>}
      </main>
    </div>
  );
}
