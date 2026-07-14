import DifficultyBadge from '../common/DifficultyBadge'
import type { StatsBossItem } from '../../types'

interface Props {
  items: StatsBossItem[]
}

export default function ItemAcquisitionList({ items }: Props) {
  if (items.length === 0) {
    return (
      <div className="rounded-xl bg-[#2d2d44] p-8 text-center text-sm text-white/40">
        획득 기록이 없습니다
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-xl bg-[#2d2d44]">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-left text-white/50">
            <th className="px-4 py-3 font-medium">날짜</th>
            <th className="px-4 py-3 font-medium">캐릭터</th>
            <th className="px-4 py-3 font-medium">보스</th>
            <th className="px-4 py-3 font-medium">난이도</th>
            <th className="px-4 py-3 font-medium">아이템</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {items.map((item, idx) => (
            <tr key={idx} className="text-white/80 hover:bg-white/5">
              <td className="px-4 py-3 tabular-nums">{item.acquiredDate}</td>
              <td className="px-4 py-3">{item.characterName}</td>
              <td className="px-4 py-3">{item.bossName}</td>
              <td className="px-4 py-3">
                <DifficultyBadge difficulty={item.difficulty} />
              </td>
              <td className="px-4 py-3">{item.itemName}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
