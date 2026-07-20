import { getSpriteUrl } from '../services/api'

export default function PokemonCard({ pokemon, selecionado, onClick }) {
  return (
    <div
      onClick={() => onClick(pokemon)}
      title={pokemon.nome}
      style={{
        width: '100px', height: '100px', // Aumentado para 100px para alinhar com a nova grade
        background: selecionado ? '#A8D8A8' : '#C8C8C8',
        border: selecionado ? '3px solid #2E7D32' : '3px solid #888888',
        borderBottom: selecionado ? '4px solid #1B5E20' : '4px solid #555555',
        cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative', imageRendering: 'pixelated',
        transition: 'transform 0.1s, border-color 0.1s',
        transform: selecionado ? 'scale(1.08)' : 'scale(1)',
      }}
    >
      <img
        src={getSpriteUrl(pokemon.id)}
        alt={pokemon.nome}
        style={{
          width: '80px', height: '80px', // Aumentado de 56px para 80px para destacar o sprite
          imageRendering: 'pixelated',
          filter: selecionado ? 'none' : 'brightness(0.9)',
        }}
        draggable={false}
      />
      {selecionado && (
        <div style={{
          position: 'absolute', inset: 0,
          border: '2px dashed #4CAF50',
          pointerEvents: 'none',
        }} />
      )}
    </div>
  )
}