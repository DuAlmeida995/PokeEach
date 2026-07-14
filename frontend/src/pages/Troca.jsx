import { useState } from 'react'
import { getSpriteUrl, USUARIOS_ONLINE, INVENTARIO_RIVAL } from '../services/mockData'

export default function Troca({ meuPokemon, onVoltar }) {
  const [rivalSelecionado,  setRivalSelecionado]  = useState(null)
  const [pokemonRival,      setPokemonRival]       = useState(null)
  const [confirmando,       setConfirmando]        = useState(false)
  const [sucesso,           setSucesso]            = useState(false)

  const inventarioRival = rivalSelecionado
    ? (INVENTARIO_RIVAL[rivalSelecionado.chave] ?? [])
    : []

  function selecionarRival(usuario) {
    setRivalSelecionado(usuario)
    setPokemonRival(null)
  }

  async function confirmarTroca() {
    if (!meuPokemon || !pokemonRival || !rivalSelecionado) return
    setConfirmando(true)
    // Mock: simula envio da TX para a rede
    await new Promise(r => setTimeout(r, 1800))
    setConfirmando(false)
    setSucesso(true)
  }

  if (sucesso) {
    return <TelaSucesso meuPokemon={meuPokemon} pokemonRival={pokemonRival} onVoltar={onVoltar} />
  }

  return (
    <div style={{
      flex: 1,
      background: '#3050C8',
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      overflow: 'hidden',
    }}>
      {/* Área principal */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '0px',
        padding: '20px',
        position: 'relative',
      }}>

        {/* Card do MEU Pokémon */}
        <PokemonTradeCard
          titulo="Seu Pokémon"
          pokemon={meuPokemon}
          selecionado={true}
          subtitulo="oferecendo"
        />

        {/* Pokébola central + seta */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '8px',
          zIndex: 2,
          margin: '0 -10px',
        }}>
          <img
            src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png"
            alt="Pokébola"
            style={{
              width: '64px',
              height: '64px',
              imageRendering: 'pixelated',
              filter: pokemonRival ? 'brightness(1.2)' : 'brightness(0.7)',
              transition: 'filter 0.3s',
            }}
          />
          <span style={{ fontFamily: 'var(--font)', fontSize: '7px', color: 'rgba(255,255,255,0.5)' }}>
            ⇄
          </span>
        </div>

        {/* Card do Pokémon rival */}
        {rivalSelecionado ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', alignItems: 'center' }}>
            {/* Inventário do rival */}
            <div style={{
              background: 'rgba(0,0,0,0.3)',
              border: '3px solid rgba(255,255,255,0.2)',
              padding: '10px',
              display: 'flex',
              gap: '8px',
              flexWrap: 'wrap',
              maxWidth: '260px',
              justifyContent: 'center',
            }}>
              <p style={{
                width: '100%',
                fontFamily: 'var(--font)',
                fontSize: '6px',
                color: 'rgba(255,255,255,0.7)',
                textAlign: 'center',
                marginBottom: '4px',
              }}>
                {rivalSelecionado.nome} — escolha um:
              </p>
              {inventarioRival.length === 0 ? (
                <p style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#F8C800' }}>
                  Sem Pokémon disponíveis
                </p>
              ) : inventarioRival.map(p => (
                <div
                  key={p.id}
                  onClick={() => setPokemonRival(p)}
                  title={p.nome}
                  style={{
                    width: '60px', height: '60px',
                    background: pokemonRival?.id === p.id ? '#A8D8A8' : '#C8C8C8',
                    border: pokemonRival?.id === p.id ? '3px solid #4CAF50' : '3px solid #888',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    transition: 'transform 0.1s',
                    transform: pokemonRival?.id === p.id ? 'scale(1.1)' : 'scale(1)',
                  }}
                >
                  <img
                    src={getSpriteUrl(p.id)}
                    alt={p.nome}
                    style={{ width: '44px', height: '44px', imageRendering: 'pixelated' }}
                  />
                </div>
              ))}
            </div>

            {pokemonRival && (
              <PokemonTradeCard
                titulo={rivalSelecionado.nome}
                pokemon={pokemonRival}
                selecionado={false}
                subtitulo="recebendo"
              />
            )}
          </div>
        ) : (
          /* Placeholder quando nenhum rival está selecionado */
          <div style={{
            width: '180px',
            height: '200px',
            background: 'rgba(255,255,255,0.08)',
            border: '3px dashed rgba(255,255,255,0.25)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
          }}>
            <p style={{
              fontFamily: 'var(--font)',
              fontSize: '7px',
              color: 'rgba(255,255,255,0.5)',
              textAlign: 'center',
              lineHeight: '2',
            }}>
              Selecione um treinador online ao lado
            </p>
          </div>
        )}
      </div>

      {/* Barra inferior com botões */}
      <div style={{
        background: 'rgba(0,0,0,0.35)',
        borderTop: '3px solid rgba(255,255,255,0.1)',
        padding: '12px 24px',
        display: 'flex',
        gap: '16px',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        <PixelBtn onClick={onVoltar} color="#D8D8D8" textColor="#181818">
          ◀ Voltar
        </PixelBtn>

        <PixelBtn
          onClick={confirmarTroca}
          disabled={!pokemonRival || confirmando}
          color={pokemonRival && !confirmando ? '#D8D8D8' : '#888888'}
          textColor="#181818"
        >
          {confirmando ? '⏳ Enviando TX...' : 'Confirmar Troca'}
        </PixelBtn>
      </div>
    </div>
  )
}

// ── Sub-componentes ──────────────────────────────────────────

function PokemonTradeCard({ titulo, pokemon, subtitulo }) {
  return (
    <div style={{
      background: '#D8D8D8',
      border: '4px solid #888888',
      borderBottom: '6px solid #555555',
      padding: '12px',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: '8px',
      minWidth: '160px',
    }}>
      <span style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#444', textTransform: 'uppercase' }}>
        {subtitulo}
      </span>
      <span style={{ fontFamily: 'var(--font)', fontSize: '9px', color: '#181818' }}>
        {pokemon.nome}
      </span>
      <img
        src={getSpriteUrl(pokemon.id)}
        alt={pokemon.nome}
        style={{ width: '80px', height: '80px', imageRendering: 'pixelated' }}
      />
      <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#666' }}>
        {titulo}
      </span>
    </div>
  )
}

function PixelBtn({ children, onClick, disabled, color, textColor }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        background: color,
        border: '3px solid #555',
        borderBottom: '5px solid #333',
        fontFamily: 'var(--font)',
        fontSize: '7px',
        color: textColor,
        padding: '10px 16px',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.7 : 1,
        transition: 'transform 0.1s',
      }}
    >
      {children}
    </button>
  )
}

function TelaSucesso({ meuPokemon, pokemonRival, onVoltar }) {
  return (
    <div style={{
      flex: 1,
      background: '#3050C8',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '24px',
      height: '100vh',
    }}>
      <p style={{ fontFamily: 'var(--font)', fontSize: '10px', color: '#F8C800', textAlign: 'center', lineHeight: 2 }}>
        Troca realizada!<br />TX enviada à rede ✓
      </p>
      <div style={{ display: 'flex', gap: '32px', alignItems: 'center' }}>
        <PokemonTradeCard titulo="você recebeu" pokemon={pokemonRival} subtitulo="novo" />
      </div>
      <button
        onClick={onVoltar}
        style={{
          background: '#D8D8D8',
          border: '3px solid #888',
          borderBottom: '5px solid #555',
          fontFamily: 'var(--font)',
          fontSize: '8px',
          color: '#181818',
          padding: '12px 20px',
          cursor: 'pointer',
          marginTop: '8px',
        }}
      >
        Voltar ao Inventário
      </button>
    </div>
  )
}