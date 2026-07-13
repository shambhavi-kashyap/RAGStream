import { useState } from 'react';

function App() {
  const [tenantId, setTenantId] = useState('tenant_xyz_123');
  const [file, setFile] = useState<File | null>(null);

  const handleUpload = async () => {
    if (!file) return;
    
    // In the future, this POSTs to your Java Gateway, which drops an event into Kafka
    console.log(`Uploading ${file.name} for ${tenantId} into the RAG Pipeline...`);
  };

  return (
    <div className="p-10 max-w-xl mx-auto font-sans">
      <h1 className="text-2xl font-bold mb-4">Enterprise RAG Ingestion</h1>
      
      <div className="flex flex-col gap-4">
        <input 
          type="text" 
          value={tenantId} 
          onChange={(e) => setTenantId(e.target.value)} 
          className="border p-2 rounded"
        />
        <input 
          type="file" 
          onChange={(e) => setFile(e.target.files?.[0] || null)} 
          className="border p-2 rounded"
        />
        <button 
          onClick={handleUpload}
          className="bg-blue-600 text-white p-2 rounded hover:bg-blue-700"
        >
          Start Ingestion Pipeline
        </button>
      </div>
    </div>
  );
}

export default App;