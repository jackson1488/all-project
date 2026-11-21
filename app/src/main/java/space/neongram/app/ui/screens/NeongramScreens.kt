package space.neongram.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.neongram.app.core.model.ChatDto
import space.neongram.app.core.model.MessageDto
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

@Composable
fun NeongramRootScreen(viewModel: NeongramViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scheme = LocalNeonScheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
    ) {
        if (!state.session.isAuthenticated) {
            AuthScreen(
                state = state,
                onLogin = { u, p, url -> viewModel.login(u, p, url) },
                onRegister = { u, p, url -> viewModel.register(u, p, url) }
            )
        } else if (state.activeChat != null) {
            ChatRoomScreen(
                chat = state.activeChat!!,
                currentUserId = state.session.userId,
                messages = state.messages,
                isLoading = state.isLoading,
                onBack = { viewModel.closeActiveChat() },
                onSend = { text -> viewModel.sendMessage(text) }
            )
        } else {
            MainTabsScreen(
                state = state,
                onOpenChat = { viewModel.openChat(it) },
                onRefreshChats = { viewModel.refreshChats() },
                onSearchUsers = { viewModel.searchUsers(it) },
                onStartDirectChat = { viewModel.startDirectChat(it) },
                onUpdateProfile = { name, bio -> viewModel.updateMyProfile(name, bio) },
                onLogout = { viewModel.logout() }
            )
        }

        state.errorMessage?.let { err ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = scheme.card,
                contentColor = scheme.textPrimary,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK", color = scheme.accentCyan)
                    }
                }
            ) {
                Text(err)
            }
        }
    }
}

@Composable
fun AuthScreen(
    state: NeongramUiState,
    onLogin: (String, String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    val scheme = LocalNeonScheme.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://cloud.neongram.space") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(scheme.accentCyan, scheme.accentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "N",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.background
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NEONGRAM",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = scheme.textPrimary,
            letterSpacing = 2.sp
        )
        Text(
            text = "Native Kotlin Client • cloud.neongram.space",
            fontSize = 13.sp,
            color = scheme.accentCyan
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Сервер Backend") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isRegisterMode) {
                        onRegister(username, password, serverUrl)
                    } else {
                        onLogin(username, password, serverUrl)
                    }
                },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.accentCyan,
                    contentColor = scheme.background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = scheme.background,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isRegisterMode) "Создать аккаунт" else "Войти в Neongram",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = { isRegisterMode = !isRegisterMode },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Регистрация",
                    color = scheme.textSecondary
                )
            }
        }
    }
}

@Composable
fun MainTabsScreen(
    state: NeongramUiState,
    onOpenChat: (ChatDto) -> Unit,
    onRefreshChats: () -> Unit,
    onSearchUsers: (String) -> Unit,
    onStartDirectChat: (UserDto) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Neongram",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (state.isSocketConnected) scheme.onlineGreen else scheme.accentPink)
                    )
                }

                IconButton(onClick = onRefreshChats) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = scheme.accentCyan
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = scheme.surface,
                contentColor = scheme.textPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Чаты") },
                    label = { Text("Чаты") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PersonSearch, contentDescription = "Контакты") },
                    label = { Text("Поиск") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                    label = { Text("Профиль") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatsListTab(
                    chats = state.chats,
                    currentUserId = state.session.userId,
                    onOpenChat = onOpenChat
                )
                1 -> ContactsSearchTab(
                    results = state.searchResults,
                    onSearch = onSearchUsers,
                    onStartChat = onStartDirectChat
                )
                2 -> ProfileTab(
                    user = state.currentUser,
                    session = state.session,
                    isSocketConnected = state.isSocketConnected,
                    onUpdateProfile = onUpdateProfile,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
fun ChatsListTab(
    chats: List<ChatDto>,
    currentUserId: String,
    onOpenChat: (ChatDto) -> Unit
) {
    val scheme = LocalNeonScheme.current
    var filterQuery by remember { mutableStateOf("") }

    val filtered = remember(chats, filterQuery) {
        if (filterQuery.isBlank()) chats
        else chats.filter {
            it.resolveDisplayTitle(currentUserId).contains(filterQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Поиск по чатам...") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Чатов пока нет. Найдите друзей во вкладке Поиск!",
                    color = scheme.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { chat ->
                    val title = chat.resolveDisplayTitle(currentUserId)
                    val lastMsg = chat.messages.firstOrNull()
                    val peer = chat.resolvePeerUser(currentUserId)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(scheme.card)
                            .border(1.dp, scheme.cardBorder, RoundedCornerShape(20.dp))
                            .clickable { onOpenChat(chat) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(scheme.accentPurple, scheme.accentCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = scheme.background
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = title,
                                    color = scheme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (peer?.isOnline == true) {
                                    Text(
                                        text = "online",
                                        color = scheme.onlineGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = lastMsg?.content?.takeIf { it.isNotBlank() }
                                    ?: if (lastMsg != null) "[${lastMsg.type}]" else "Нет сообщений",
                                color = scheme.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsSearchTab(
    results: List<UserDto>,
    onSearch: (String) -> Unit,
    onStartChat: (UserDto) -> Unit
) {
    val scheme = LocalNeonScheme.current
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            placeholder = { Text("Введите @username пользователя...") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.card)
                        .border(1.dp, scheme.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { onStartChat(user) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(scheme.accentCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.effectiveName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = scheme.background
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.effectiveName,
                            color = scheme.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user.username}",
                            color = scheme.accentCyan,
                            fontSize = 13.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Написать",
                        tint = scheme.accentCyan
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileTab(
    user: UserDto?,
    session: space.neongram.app.core.session.StoredSession,
    isSocketConnected: Boolean,
    onUpdateProfile: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: session.displayName) }
    var bio by remember(user) { mutableStateOf(user?.bio ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(scheme.accentCyan, scheme.accentPurple))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user?.effectiveName ?: session.username).take(1).uppercase(),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.background
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "@${user?.username ?: session.username}",
            color = scheme.accentCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Text(
            text = if (isSocketConnected) "Realtime Socket.IO: Подключен к ${session.serverUrl}"
            else "Socket.IO: Переподключение...",
            color = if (isSocketConnected) scheme.onlineGreen else scheme.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Отображаемое имя") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("О себе (Bio)") },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onUpdateProfile(displayName, bio) },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.accentCyan,
                contentColor = scheme.background
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Сохранить профиль", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Выйти из аккаунта", color = scheme.dangerRed)
        }
    }
}

@Composable
fun ChatRoomScreen(
    chat: ChatDto,
    currentUserId: String,
    messages: List<MessageDto>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    val scheme = LocalNeonScheme.current
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surface)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = scheme.accentCyan
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.resolveDisplayTitle(currentUserId),
                    color = scheme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (chat.type == "GROUP") "Групповой чат" else "Личный чат",
                    color = scheme.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (isLoading && messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = scheme.accentCyan)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId || msg.sender?.id == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 290.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isMine) 18.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 18.dp
                                    )
                                )
                                .background(if (isMine) scheme.outgoingBubble else scheme.incomingBubble)
                                .border(
                                    1.dp,
                                    if (isMine) scheme.accentPurple.copy(alpha = 0.45f) else scheme.cardBorder,
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isMine) 18.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 18.dp
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (!isMine && msg.sender != null) {
                                Text(
                                    text = msg.sender.effectiveName,
                                    color = scheme.accentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = msg.content?.takeIf { it.isNotBlank() } ?: "[${msg.type}]",
                                color = scheme.textPrimary,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Сообщение...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val toSend = text.trim()
                    if (toSend.isNotEmpty()) {
                        text = ""
                        onSend(toSend)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scheme.accentCyan)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = scheme.background
                )
            }
        }
    }
}
