import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export const useIngestionProgress = (tenantId: string) => {
    const [progress, setProgress] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    if (!tenantId || !isProcessing) return;

    const socket = new SockJS('http://localhost:8080/ws-progress');
    const stompClient = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log('[WebSocket Debug]:', str),
      reconnectDelay: 5000, 
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = (frame) => {
      console.log('Connected to Ingestion WebSocket Broker');

      stompClient.subscribe(`/topic/progress/${tenantId}`, (message) => {
        if (message.body) {
          const data = JSON.parse(message.body);
          setProgress(data.progress);

          if (data.progress >= 100) {
            setIsProcessing(false);
          }
        }
      });
    };

    stompClient.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    stompClient.activate();

    return () => {
      if (stompClient.active) {
        stompClient.deactivate();
      }
    };
  }, [tenantId, isProcessing]);

  return { progress, isProcessing, setIsProcessing, setProgress };
};