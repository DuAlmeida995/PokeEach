import React from 'react'

export default function Sidebar({ usuarios, onSelecionarUsuario, usuarioSelecionado }) {
  return (
    <aside style={{
      width: '160px',
      minWidth: '160px',
      background: '#C8181C',
      display: 'flex',
      flexDirection: 'column',
      borderRight: '4px solid #8B0000',
      height: '100vh',
      overflow: 'hidden',
    }}>
      {/* Logo */}
      <div style={{
        padding: '12px 8px 8px',
        borderBottom: '4px solid #8B0000',
        textAlign: 'center',
      }}>
        <span style={{
          fontFamily: 'var(--font)',
          fontSize: '18px',
          color: '#F8C800',
          textShadow: '2px 2px 0 #8B0000, -1px -1px 0 #FF6600',
          letterSpacing: '-1px',
        }}>
          Pok<span style={{ color: '#F8F8F8' }}>Each</span>
        </span>
      </div>

      {/* Online label */}
      <div style={{ padding: '10px 10px 4px', }}>
        <span style={{
          fontFamily: 'var(--font)',
          fontSize: '7px',
          color: '#90EE90',
          letterSpacing: '1px',
        }}>
          ● Online
        </span>
        <hr style={{ border: '1px solid #8B0000', marginTop: '6px' }} />
      </div>

      {/* Lista de usuários */}
      <ul style={{
        listStyle: 'none',
        padding: '4px 0',
        overflowY: 'auto',
        flex: 1,
      }}>
        {usuarios.map(u => (
          <li
            key={u.chave}
            onClick={() => onSelecionarUsuario(u)}
            style={{
              padding: '8px 10px',
              fontFamily: 'var(--font)',
              fontSize: '6.5px',
              color: usuarioSelecionado?.chave === u.chave ? '#F8C800' : '#FFB3B3',
              cursor: 'pointer',
              background: usuarioSelecionado?.chave === u.chave ? '#8B0000' : 'transparent',
              borderLeft: usuarioSelecionado?.chave === u.chave ? '3px solid #F8C800' : '3px solid transparent',
              transition: 'all 0.1s',
              wordBreak: 'break-all',
              lineHeight: '1.6',
            }}
          >
            {u.nome}
          </li>
        ))}
      </ul>

      {/* Botão de minerar — botão de grama */}
      <div style={{ padding: '12px 10px', borderTop: '4px solid #8B0000' }}>
        <MinerarButton />
      </div>
    </aside>
  )
}

function MinerarButton() {
  const [minerando, setMinerando] = React.useState(false)
  const [resultado, setResultado] = React.useState(null)

  async function handleMinerar() {
    setMinerando(true)
    setResultado(null)
    // Mock: simula delay de mineração
    await new Promise(r => setTimeout(r, 1500))
    const pokemon = ['Pikachu', 'Eevee', 'Meowth', 'Snorlax', 'Mew'][Math.floor(Math.random() * 5)]
    setResultado(pokemon)
    setMinerando(false)
  }

  return (
    <div>
      <button
        onClick={handleMinerar}
        disabled={minerando}
        title="Minerar um Pokémon"
        style={{
          width: '100%',
          height: '44px',
          background: minerando
            ? '#2E7D32'
            : 'repeating-linear-gradient(45deg, #4CAF50 0px, #4CAF50 4px, #3a8f3e 4px, #3a8f3e 8px)',
          border: '3px solid #2E7D32',
          borderBottom: '5px solid #1B5E20',
          cursor: minerando ? 'wait' : 'pointer',
          fontFamily: 'var(--font)',
          fontSize: '6px',
          color: '#F8F8F8',
          textShadow: '1px 1px 0 #1B5E20',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '4px',
          imageRendering: 'pixelated',
          transition: 'transform 0.1s',
          transform: minerando ? 'translateY(2px)' : 'none',
        }}
      >
        {minerando ? '⛏ Minerando...' : '🌿 Minerar'}
      </button>
      {resultado && (
        <p style={{
          fontFamily: 'var(--font)',
          fontSize: '6px',
          color: '#F8C800',
          marginTop: '6px',
          textAlign: 'center',
          lineHeight: '1.8',
        }}>
          Você capturou<br />{resultado}!
        </p>
      )}
    </div>
  )
}