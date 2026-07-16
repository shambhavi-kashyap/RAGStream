import React, { useState, useEffect } from 'react';
import Login from '../Login';
import Register from '../Register';
import { useIngestionProgress } from './useIngestionProgress';

type PipelineState = 'IDLE' | 'UPLOADING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXED' | 'ERROR';

function ChatInterface({ tenantId }: { tenantId: string }) {
  const [query, setQuery] = useState('');
  const [chatHistory, setChatHistory] = useState<Array<{role: string, text: string, meta?: any}>>([]);
  const [isTyping, setIsTyping] = useState(false);

  const handleAskQuestion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    const userMessage = query;
    setChatHistory(prev => [...prev, { role: 'user', text: userMessage }]);
    setQuery('');
    setIsTyping(true);

    try {
      const response = await fetch('http://localhost:8000/api/v1/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tenant_id: tenantId,
          user_query: userMessage
        })
      });

      const data = await response.json();

      if (response.ok) {
        setChatHistory(prev => [...prev, { 
          role: 'ai', 
          text: data.answer,
          meta: { source: data.source, latency: data.latency_ms }
        }]);
      } else {
        setChatHistory(prev => [...prev, { role: 'ai', text: "❌ Error: " + (data.detail || "Something went wrong.") }]);
      }
    } catch (error) {
      setChatHistory(prev => [...prev, { role: 'ai', text: "❌ Network Error: Could not reach Python gateway." }]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 mt-8">
      <h2 className="text-lg font-semibold mb-4">Enterprise RAG Chat</h2>
      
      <div className="bg-slate-50 border border-slate-200 rounded-lg h-96 p-4 mb-4 overflow-y-auto flex flex-col gap-4">
        {chatHistory.length === 0 ? (
          <div className="text-slate-400 text-center mt-10 text-sm">
            Upload a document, then ask a question to search the vector database!
          </div>
        ) : (
          chatHistory.map((msg, idx) => (
            <div key={idx} className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
              <div className={`p-3 rounded-lg max-w-[80%] ${msg.role === 'user' ? 'bg-blue-600 text-white rounded-br-none' : 'bg-white border border-slate-200 text-slate-800 rounded-bl-none shadow-sm'}`}>
                {msg.text}
              </div>
              {msg.meta && (
                <span className="text-[10px] text-slate-400 mt-1 ml-1 font-mono">
                  ⚡ {msg.meta.latency}ms | 🔍 {msg.meta.source === 'redis_semantic_cache' ? 'Redis Cache' : 'Qdrant DB'}
                </span>
              )}
            </div>
          ))
        )}
        {isTyping && (
          <div className="text-slate-400 text-sm animate-pulse ml-2">AI is thinking...</div>
        )}
      </div>

      <form onSubmit={handleAskQuestion} className="flex gap-2">
        <input 
          type="text" 
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask a question about your documents..." 
          className="flex-1 border border-slate-300 p-3 rounded-md focus:ring-2 focus:ring-blue-500 outline-none"
          disabled={isTyping}
        />
        <button 
          type="submit" 
          disabled={isTyping || !query.trim()}
          className="bg-slate-900 text-white px-6 rounded-md font-medium hover:bg-slate-800 disabled:bg-slate-300 transition-colors"
        >
          Send
        </button>
      </form>
    </div>
  );
}

function Dashboard({ onLogout }: { onLogout: () => void }) {
  const [tenantId, setTenantId] = useState('apple_inc');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<PipelineState>('IDLE');
  const [logs, setLogs] = useState<string[]>([]);

  const { progress, isProcessing, setIsProcessing, setProgress } = useIngestionProgress(tenantId);

  const addLog = (message: string) => {
    const timestamp = new Date().toISOString().split('T')[1].substring(0, 8);
    setLogs((prev) => [...prev, `[${timestamp}] ${message}`]);
  };

  useEffect(() => {
    if (progress > 0 && progress < 100 && status === 'CHUNKING') {
      setStatus('EMBEDDING');
      addLog(`🧠 Java Worker: Generating embeddings...`);
    } else if (progress >= 100 && status !== 'INDEXED') {
      setStatus('INDEXED');
      addLog("✅ SUCCESS: Document fully indexed into Qdrant Vector DB.");
      setIsProcessing(false);
    }
  }, [progress, status, setIsProcessing]);

  const handleUpload = async () => {
    if (!file) return;
    setProgress(0);
    setLogs([]);
    setStatus('UPLOADING');
    addLog(`📤 INITIATED: Uploading ${file.name} to gateway...`);

    setIsProcessing(true); 

    const formData = new FormData();
    formData.append('file', file);
    formData.append('tenant_id', tenantId);

    try {
      const response = await fetch('http://localhost:8000/api/v1/upload-pdf', {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        addLog("✅ Chunks securely queued in Kafka.");
        setStatus('CHUNKING');
      } else {
        addLog("❌ Upload failed at gateway level.");
        setStatus('ERROR');
        setIsProcessing(false); 
      }
    } catch (error) {
      console.error("Upload error:", error);
      addLog("❌ Network error connecting to Python gateway.");
      setStatus('ERROR');
      setIsProcessing(false); 
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-10 font-sans text-slate-900">
      <div className="max-w-3xl mx-auto space-y-8">
        
        {/* Header Section */}
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-900">RAGStream Admin</h1>
            <p className="text-slate-500 mt-2">Distributed Multi-Tenant Ingestion Gateway</p>
          </div>
          <button 
            onClick={onLogout}
            className="text-sm bg-red-50 text-red-600 px-4 py-2 rounded-md font-medium hover:bg-red-100 transition-colors"
          >
            Sign Out
          </button>
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
                disabled={status !== 'IDLE' && status !== 'INDEXED' && status !== 'ERROR'}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Corpus File (PDF/TXT)</label>
              <input 
                type="file" 
                onChange={(e) => setFile(e.target.files?.[0] || null)} 
                className="w-full border border-slate-300 p-2 rounded-md bg-slate-50"
                disabled={status !== 'IDLE' && status !== 'INDEXED' && status !== 'ERROR'}
              />
            </div>
            <button 
              onClick={handleUpload}
              disabled={!file || (status !== 'IDLE' && status !== 'INDEXED' && status !== 'ERROR')}
              className="mt-2 bg-blue-600 text-white py-2 px-4 rounded-md font-medium hover:bg-blue-700 disabled:bg-slate-300 transition-colors"
            >
              {status === 'IDLE' || status === 'INDEXED' || status === 'ERROR' ? 'Start Ingestion Pipeline' : 'Pipeline Running...'}
            </button>
          </div>
        </div>

        {/* Real-Time Observability Dashboard */}
        {status !== 'IDLE' && (
          <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 space-y-4">
            <div className="flex justify-between items-center">
              <h2 className="text-lg font-semibold">Pipeline Status: <span className="text-blue-600">{status}</span></h2>
              <span className="text-sm font-medium text-slate-500">{Math.round(progress)}%</span>
            </div>
            
            <div className="w-full bg-slate-100 rounded-full h-3 border border-slate-200 overflow-hidden">
              <div 
                className={`h-3 rounded-full transition-all duration-300 ease-out ${progress === 100 ? 'bg-green-500' : 'bg-blue-600'}`}
                style={{ width: `${progress}%` }}
              ></div>
            </div>

            <div className="mt-6 bg-slate-900 rounded-lg p-4 h-48 overflow-y-auto font-mono text-xs shadow-inner flex flex-col justify-end">
              {logs.map((log, index) => (
                <div key={index} className="text-green-400 mb-1">
                  {log}
                </div>
              ))}
              {isProcessing && (
                <div className="text-slate-500 animate-pulse mt-2">_ awaiting events from Kafka...</div>
              )}
            </div>
          </div>
        )}

        <ChatInterface tenantId={tenantId} />

      </div>
    </div>
  );
}

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentView, setCurrentView] = useState<'login' | 'register'>('login');

  useEffect(() => {
    const token = localStorage.getItem('ragstream_jwt');
    if (token) {
      setIsAuthenticated(true);
    }
  }, []);

  const handleAuthSuccess = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    localStorage.removeItem('ragstream_jwt');
    setIsAuthenticated(false);
  };

  if (isAuthenticated) {
    return <Dashboard onLogout={handleLogout} />;
  }

  return currentView === 'login' ? (
    <Login 
      onLoginSuccess={handleAuthSuccess} 
      navigateToRegister={() => setCurrentView('register')} 
    />
  ) : (
    <Register 
      onRegisterSuccess={handleAuthSuccess} 
      navigateToLogin={() => setCurrentView('login')} 
    />
  );
}