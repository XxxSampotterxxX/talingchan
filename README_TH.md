# Battle of Talingchan – ตัวติดตั้ง Windows

## ตั้งค่าครั้งแรก (ทำครั้งเดียว)
1. สมัคร/ล็อกอิน github.com แล้วสร้าง repo ชื่อ `talingchan` (Public เพื่อให้อัพเดทดาวน์โหลดได้ฟรี)
2. แก้ `package.json` บรรทัด `"owner": "YOUR_GITHUB_USERNAME"` เป็นชื่อ GitHub ของคุณ
3. อัพโหลดไฟล์ทั้งโฟลเดอร์นี้ขึ้น repo (ปุ่ม Add file > Upload files ลากทั้งโฟลเดอร์ รวมโฟลเดอร์ .github)
4. สร้าง Release แรก: แท็บ Releases > Create a new release > ตั้ง tag `v1.0.0` > Publish
   GitHub จะ build ให้เองประมาณ 3-5 นาที (ดูที่แท็บ Actions) แล้วแนบไฟล์ `Battle-of-Talingchan-Setup-1.0.0.exe` ใน Release
5. ดาวน์โหลด .exe ไปติดตั้ง

## ออกอัพเดทตัวแอป
แก้ `version` ใน package.json (เช่น 1.0.1) แล้วสร้าง Release tag `v1.0.1`
แอปที่ติดตั้งไว้จะเด้งถามตอนเปิดครั้งถัดไป กด "อัพเดทเลย" ไม่ต้องติดตั้งใหม่

## หมายเหตุ
- เนื้อหาเกม (กติกา การ์ด หน้าตา) โหลดสดจากหน้า Artifact ทุกครั้งที่เปิด จึงได้เวอร์ชันล่าสุดอัตโนมัติ ไม่ต้องออกตัวติดตั้งใหม่
- ต้องล็อกอิน claude.ai ในหน้าต่างเกมครั้งแรก (ระบบจำให้) และต้องต่ออินเทอร์เน็ต
- ตัวติดตั้งยังไม่ได้เซ็นรับรอง Windows อาจเตือน SmartScreen ให้กด More info > Run anyway
