import { create } from 'zustand'
import type { Character } from '../types'

interface CharacterState {
  characters: Character[]
  setCharacters: (chars: Character[]) => void
  updateFavorite: (id: number, favorite: boolean) => void
}

export const useCharacterStore = create<CharacterState>((set) => ({
  characters: [],
  setCharacters: (characters) => set({ characters }),
  updateFavorite: (id, favorite) =>
    set((state) => ({
      characters: state.characters.map((c) =>
        c.id === id ? { ...c, favorite } : c,
      ),
    })),
}))
