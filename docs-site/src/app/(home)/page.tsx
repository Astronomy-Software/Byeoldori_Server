import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <h1 className="text-5xl font-bold tracking-tight">⭐ 별도리 API</h1>
      <p className="text-xl text-fd-muted-foreground">별도리 백엔드 서버 API 레퍼런스</p>
      <Link
        href="/docs"
        className="rounded-md bg-fd-primary px-6 py-3 text-fd-primary-foreground font-medium hover:bg-fd-primary/90 transition-colors"
      >
        API 문서 보기 →
      </Link>
    </main>
  );
}
