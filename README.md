# comparIT (Java, no external libraries)

## Run

Needs JDK 17+.

### Option 1: 1-Click Launch (Windows)
Double-click `run.bat` in this folder. It compiles and launches the server, opening your browser to `http://localhost:8080`.

### Option 2: Terminal / PowerShell
From the project root folder:

```powershell
javac -cp "lib\*" -d out src\grocery\*.java src\grocery\model\*.java src\grocery\service\*.java
java -cp "out;lib\*" grocery.Main
```

Open http://localhost:8080 (Press `Ctrl+C` in terminal to stop).

### Option 3: VS Code / Eclipse
- **VS Code**: Open this folder, open `src/grocery/Main.java`, click "Run".
- **Eclipse**: File > New > Java Project, add JARs from `lib/` to Build Path, run `grocery.Main`.
(Working directory must be the project root so `data/` and `web/` are found.)

## MongoDB Database (`comparit_db`)

The project connects to your local MongoDB service (`localhost:27017`):

### How to view in MongoDB Compass:
1. Open **MongoDB Compass**.
2. Connect to your active connection (e.g. `localhost:27017` / `Java_pbl`).
3. Click the **Refresh** button on the top right.
4. You will see **`comparit_db`** in your database list!
5. Inside **`comparit_db`**, you will find:
   - **`products`**: All 231 grocery catalogue items with names, categories, emojis, prices, and images.
   - **`cart`**: Active user shopping cart items with quantities and product details, synced live.
   - **`favourites`**: User saved items.
   - **`recent_searches`**: Recent user search queries with timestamps.

## Offline Support
The entire application runs **100% offline without an internet connection**:
- Built with standard Java standard library (`com.sun.net.httpserver`, no external dependencies or downloads required).
- All 231 brand product photos and produce images are stored locally in `web/images/`.
- Frontend styles, icons, and scripts are self-contained with no external CDN or web font dependencies.

