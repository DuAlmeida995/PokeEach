export default function IVDisplay({ pokemon, compact = false }) {
  if (!pokemon) return null

  const ivs = [
    { key: 'hp',  label: 'HP',   color: '#58C858' },
    { key: 'atk', label: 'ATK',  color: '#F85840' },
    { key: 'def', label: 'DEF',  color: '#F8A800' },
    { key: 'spa', label: 'SP.A', color: '#6890F0' },
    { key: 'spd', label: 'SP.D', color: '#78C850' },
    { key: 'spe', label: 'VEL',  color: '#F85888' },
  ]

  const temIVs = ivs.some(({ key }) => pokemon[key] !== undefined)
  if (!temIVs) return null

  return (
    <div style={{ width: '100%' }}>
      {!compact && (
        <p style={{
          fontFamily: 'var(--font)', fontSize: '5px',
          color: '#666', marginBottom: '6px', textTransform: 'uppercase',
          letterSpacing: '1px',
        }}>
          IVs individuais
        </p>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: compact ? '3px' : '5px' }}>
        {ivs.map(({ key, label, color }) => {
          const val = pokemon[key]
          if (val === undefined) return null
          // percentual relativo ao valor maximo possivel do IV (que eh o proprio base_stat do Pokemon, armazenado como max)
          const pct = Math.min(100, Math.round((val / (val + 31)) * 100) + 20)
          return (
            <div key={key}>
              <div style={{
                display: 'flex', justifyContent: 'space-between', marginBottom: '1px',
              }}>
                <span style={{ fontFamily: 'var(--font)', fontSize: compact ? '5px' : '6px', color: '#555' }}>
                  {label}
                </span>
                <span style={{ fontFamily: 'var(--font)', fontSize: compact ? '5px' : '6px', color: '#181818', fontWeight: 'bold' }}>
                  {val}
                </span>
              </div>
              <div style={{ background: '#A8A8A8', height: compact ? '4px' : '5px', border: '1px solid #888' }}>
                <div style={{ width: `${pct}%`, height: '100%', background: color }} />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}