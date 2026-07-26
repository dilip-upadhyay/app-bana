import ReactMarkdown from 'react-markdown';
import type { ChatMessage, ToolCall } from '../../stores/chat';

function ToolCallCard({ tc }: { tc: ToolCall }) {
  const statusIcon = tc.status === 'running' ? '⟳' : tc.status === 'ok' ? '✓' : '✗';
  const statusColor = tc.status === 'running'
    ? 'text-yellow-400' : tc.status === 'ok' ? 'text-green-400' : 'text-red-400';

  const toolLabels: Record<string, string> = {
    scaffold_app:        'Scaffolding app',
    create_app:          'Creating app',
    create_entity:       'Creating entity',
    generate_page:       'Generating page',
    generate_mock_data:  'Seeding data',
    deploy_app:          'Deploying app',
    batch_update_entities: 'Updating entities',
    list_apps:           'Listing apps',
    list_entities:       'Listing entities',
    get_entity_details:  'Getting entity details',
    list_pages:          'Listing pages',
    search_knowledge:    'Searching knowledge',
    rollback_app:        'Rolling back',
  };

  return (
    <details className="mt-2 rounded-lg border border-gray-700 bg-gray-900 text-xs overflow-hidden">
      <summary className="flex items-center gap-2 px-3 py-2 cursor-pointer select-none hover:bg-gray-800">
        <span className={`${statusColor} font-mono text-base`}>{statusIcon}</span>
        <span className="text-gray-300 font-medium">{toolLabels[tc.name] ?? tc.name}</span>
        {tc.status === 'running' && (
          <span className="ml-auto text-gray-500 animate-pulse">Running…</span>
        )}
      </summary>
      <div className="px-3 pb-3 pt-1 text-gray-400 space-y-1">
        {tc.args != null && (
          <pre className="bg-gray-800 rounded p-2 overflow-x-auto text-xs leading-relaxed whitespace-pre-wrap">
            {JSON.stringify(tc.args, null, 2)}
          </pre>
        )}
        {tc.result != null && tc.status !== 'running' && (
          <pre className={`rounded p-2 overflow-x-auto text-xs leading-relaxed whitespace-pre-wrap
            ${tc.status === 'ok' ? 'bg-green-950 text-green-300' : 'bg-red-950 text-red-300'}`}>
            {typeof tc.result === 'string' ? tc.result : JSON.stringify(tc.result, null, 2)}
          </pre>
        )}
      </div>
    </details>
  );
}

export function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      {!isUser && (
        <div className="w-7 h-7 rounded-full bg-indigo-700 flex items-center justify-center text-xs
                        font-bold text-white shrink-0 mt-0.5 mr-2">
          AI
        </div>
      )}
      <div className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm
        ${isUser
          ? 'bg-indigo-600 text-white rounded-br-sm'
          : 'bg-gray-800 text-gray-100 rounded-bl-sm'}`}
      >
        {isUser ? (
          <p className="whitespace-pre-wrap">{msg.content}</p>
        ) : (
          <div className="prose-chat">
            <ReactMarkdown>{msg.content}</ReactMarkdown>
            {msg.streaming && !msg.content && (
              <span className="inline-block w-2 h-4 bg-indigo-400 animate-pulse rounded-sm" />
            )}
            {!msg.streaming && !msg.content && (msg.toolCalls?.length ?? 0) > 0 && (
              <p className="text-gray-400 italic">
                I ran the tools above but didn't produce a summary. Let me know how you'd like to proceed.
              </p>
            )}
          </div>
        )}

        {/* Tool call cards */}
        {msg.toolCalls && msg.toolCalls.length > 0 && (
          <div className="mt-2 space-y-1">
            {msg.toolCalls.map((tc) => <ToolCallCard key={tc.id} tc={tc} />)}
          </div>
        )}
      </div>
    </div>
  );
}
