import { useState, useEffect } from 'react';

// Defines the exact states your RAG pipeline goes through
type PipelineState = 'IDLE' | 'UPLOADING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXED';

function App() {
  const [tenantId, setTenantId] = useState('apple_inc');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<PipelineState>('IDLE');
  const [progress, setProgress] = useState(0);
  const [logs, setLogs] = useState<string[]>([]);

  // Simulates a real-time WebSocket connection receiving Kafka events
  useEffect(() => {
    if (status === 'IDLE' || status === 'INDEXED') return;

    const timer = setInterval(() => {
      setProgress((old) => {
        const newProgress = old + Math.random() * 15;
        if (newProgress >= 100) {
          clearInterval(timer);
          setStatus('INDEXED');
          addLog("✅ SUCCESS: Document fully indexed into Qdrant Vector DB.");
          return 100;
        }
        
        // Change status based on progress percentage to simulate pipeline stages
        if (newProgress > 20 && status === 'UPLOADING') {
          setStatus('CHUNKING');
          addLog("✂️ Strategy Pattern: Applying Semantic Overlap Chunking...");
        } else if (newProgress > 50 && status === 'CHUNKING') {
          setStatus('EMBEDDING');
          addLog(`🧠 Calling Python Gateway: Generating vectors for ${tenantId}...`);
        } else if (newProgress > 80 && status === 'EMBEDDING') {
          addLog("💾 Kafka Consumer: Batch flushing embeddings to Vector DB...");
        }
        
        return newProgress;
      });
    }, 800);

    return () => clearInterval(timer);
  }, [status, tenantId]);

  const addLog = (message: string) => {
    const timestamp = new Date().toISOString().split('T')[1].substring(0, 8);
    setLogs((prev) => [...prev, `[${timestamp}] ${message}`]);
  };

  const handleUpload = () => {
    if (!file) return;
    setProgress(0);
    setLogs([]);
    setStatus('UPLOADING');
    addLog(`📤 INITIATED: Uploading ${file.name} to ingestion queue...`);
  };

  return (
    <div className="min-h-screen bg-slate-50 p-10 font-sans text-slate-900">
      <div className="max-w-3xl mx-auto space-y-8">
        
        {/* Header Section */}
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">RAGStream Admin</h1>
          <p className="text-slate-500 mt-2">Distributed Multi-Tenant Ingestion Gateway</p>
        </div>

        {/* Configuration Card */}
        <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
          <h2 className="text-lg font-semibold mb-4">New Document Ingestion</h2>
          <div className="flex flex-col gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Target Tenant ID</label>
              <input 
                type="text" 
                value={tenantId} 
                onChange={(e) => setTenantId(e.target.value)} 
                className="w-full border border-slate-300 p-2 rounded-md focus:ring-2 focus:ring-blue-500"
                disabled={status !== 'IDLE' && status !== 'INDEXED'}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Corpus File (PDF/TXT)</label>
              <input 
                type="file" 
                onChange={(e) => setFile(e.target.files?.[0] || null)} 
                className="w-full border border-slate-300 p-2 rounded-md bg-slate-50"
                disabled={status !== 'IDLE' && status !== 'INDEXED'}
              />
            </div>
            <button 
              onClick={handleUpload}
              disabled={!file || (status !== 'IDLE' && status !== 'INDEXED')}
              className="mt-2 bg-blue-600 text-white py-2 px-4 rounded-md font-medium hover:bg-blue-700 disabled:bg-slate-300 transition-colors"
            >
              {status === 'IDLE' || status === 'INDEXED' ? 'Start Ingestion Pipeline' : 'Pipeline Running...'}
            </button>
          </div>
        </div>

        {/* Real-Time Observability Dashboard */}
        {status !== 'IDLE' && (
          <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 space-y-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
            <div className="flex justify-between items-center">
              <h2 className="text-lg font-semibold">Pipeline Status: <span className="text-blue-600">{status}</span></h2>
              <span className="text-sm font-medium text-slate-500">{Math.round(progress)}%</span>
            </div>
            
            {/* Progress Bar */}
            <div className="w-full bg-slate-100 rounded-full h-3 border border-slate-200 overflow-hidden">
              <div 
                className="bg-blue-600 h-3 rounded-full transition-all duration-500 ease-out" 
                style={{ width: `${progress}%` }}
              ></div>
            </div>

            {/* Live Terminal Logs */}
            <div className="mt-6 bg-slate-900 rounded-lg p-4 h-48 overflow-y-auto font-mono text-xs shadow-inner">
              {logs.map((log, index) => (
                <div key={index} className="text-green-400 mb-1">
                  {log}
                </div>
              ))}
              {status !== 'INDEXED' && (
                <div className="text-slate-500 animate-pulse mt-2">_ awaiting events...</div>
              )}
            </div>
          </div>
        )}

      </div>
    </div>
  );
}

export default App;