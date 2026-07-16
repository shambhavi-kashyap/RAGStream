import React, { useState } from 'react';
import api from './api'; 

export default function Register({ 
  onRegisterSuccess, 
  navigateToLogin 
}: { 
  onRegisterSuccess: () => void; 
  navigateToLogin: () => void; 
}) {  const [orgName, setOrgName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      const response = await api.post('/auth/register', {
        organizationName: orgName,
        email: email,
        password: password
      });

      const { token } = response.data;
      localStorage.setItem('ragstream_jwt', token);

      onRegisterSuccess();

    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create account. Email may be taken.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="bg-white p-8 rounded-xl shadow-sm border border-slate-200 w-full max-w-md">
        <h2 className="text-2xl font-bold text-slate-900 mb-2">Create Organization</h2>
        <p className="text-slate-500 mb-6">Setup your multi-tenant RAG environment</p>

        {error && (
          <div className="bg-red-50 text-red-600 p-3 rounded-md text-sm mb-4">
            {error}
          </div>
        )}

        <form onSubmit={handleRegister} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Organization Name</label>
            <input 
              type="text" 
              required
              value={orgName}
              onChange={(e) => setOrgName(e.target.value)}
              className="w-full border border-slate-300 p-2 rounded-md focus:ring-2 focus:ring-blue-500 outline-none"
              placeholder="e.g. Apple Inc."
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Admin Email</label>
            <input 
              type="email" 
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full border border-slate-300 p-2 rounded-md focus:ring-2 focus:ring-blue-500 outline-none"
              placeholder="admin@apple.com"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Admin Password</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full border border-slate-300 p-2 rounded-md focus:ring-2 focus:ring-blue-500 outline-none"
              placeholder="••••••••"
            />
          </div>

          <button 
            type="submit" 
            disabled={isLoading}
            className="w-full bg-blue-600 text-white py-2 rounded-md font-medium hover:bg-blue-700 disabled:bg-slate-400 transition-colors mt-4"
          >
            {isLoading ? 'Provisioning Tenant...' : 'Create Account'}
          </button>
        </form>

        <div className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{' '}
          <button onClick={navigateToLogin} className="text-blue-600 font-medium hover:underline">
            Sign in here
          </button>
        </div>
      </div>
    </div>
  );
}