"use client";

import * as React from 'react';
import {useEffect, useState} from 'react';
import {CSSObject, styled, Theme} from '@mui/material/styles';
import Box from '@mui/material/Box';
import MuiDrawer from '@mui/material/Drawer';
import MuiAppBar, {AppBarProps as MuiAppBarProps} from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import List from '@mui/material/List';
import CssBaseline from '@mui/material/CssBaseline';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded';
import WarningRoundedIcon from '@mui/icons-material/WarningRounded';
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded';
import CloudRoundedIcon from '@mui/icons-material/CloudRounded';
import NotificationsRoundedIcon from '@mui/icons-material/NotificationsRounded';
import NotificationsOffIcon from '@mui/icons-material/NotificationsOff';
import SettingsIcon from '@mui/icons-material/Settings';
import MediationIcon from '@mui/icons-material/Mediation';
import {FormControlLabel, Menu, MenuItem, Slider, Stack, Switch, Tooltip} from '@mui/material';
import Link from 'next/link';
import {Download, InsertChart, VolumeDown, VolumeUp} from "@mui/icons-material";
import AlarmAudio from "@/app/_components/AlarmAudio";
import {selectAlarmAudioVolume, setAlarmAudioVolume} from "@/lib/state/OSCARClientSlice";
import {useDispatch, useSelector} from "react-redux";
import { useBreakpoint } from '../providers';
import LanguageSelector from './LanguageSelector';
import { useLanguage } from '@/contexts/LanguageContext';
import { useRouter } from 'next/navigation';
import {setEventPreview, setSelectedRowId} from "@/lib/state/EventPreviewSlice";
import {setSelectedEvent} from "@/lib/state/EventDataSlice";
import {setEventData} from "@/lib/state/EventDetailsSlice";

const drawerWidth = 240;
const drawerWidthMobile = 200;

const openedMixin = (theme: Theme): CSSObject => ({
    width: drawerWidth,
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.enteringScreen,
    }),
    overflowX: 'hidden',
});

const closedMixin = (theme: Theme): CSSObject => ({
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    overflowX: 'hidden',
    width: `calc(${theme.spacing(7)} + 1px)`,
    [theme.breakpoints.up('sm')]: {
        width: `calc(${theme.spacing(8)} + 1px)`,
    },
});

// Used to place content below the app bar
const DrawerHeader = styled('div')(({theme}) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    padding: theme.spacing(0, 1),
    // necessary for content to be below app bar
    ...theme.mixins.toolbar,
}));

interface AppBarProps extends MuiAppBarProps {
    open?: boolean;
    isDesktop?: boolean;
}

const AppBar = styled(MuiAppBar, {
    shouldForwardProp: (prop) => prop !== 'open' && prop !== "isDesktop"
})<AppBarProps>(({theme, open, isDesktop}) => ({
    zIndex: theme.zIndex.drawer + 1,
    // Exit transition
    transition: theme.transitions.create(['width', 'margin'], {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    ...((open && isDesktop) && {
        marginLeft: drawerWidth,
        width: `calc(100% - ${drawerWidth}px)`,
        // Entrance transition
        transition: theme.transitions.create(['width', 'margin'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
        }),
    }),
    ...((open && !isDesktop) && {
        width: `calc(100% - ${drawerWidthMobile}px)`,
        // Entrance transition
        transition: theme.transitions.create(['transform'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
        }),
    }),
}));

const Drawer = styled(MuiDrawer, {shouldForwardProp: (prop) => prop !== 'open'})(
    ({theme, open}) => ({
        width: drawerWidth,
        flexShrink: 0,
        whiteSpace: 'nowrap',
        boxSizing: 'border-box',
        ...(open && {
            ...openedMixin(theme),
            '& .MuiDrawer-paper': openedMixin(theme),
        }),
        ...(!open && {
            ...closedMixin(theme),
            '& .MuiDrawer-paper': closedMixin(theme),
        }),
    }),
);

export default function Navbar({children}: { children: React.ReactNode }) {
    const { isDesktop } = useBreakpoint();

    const [settingsAnchorEl, setSettingsAnchorEl] = useState<null | HTMLElement>(null); // Anchor element for settings menu
    const settingsMenuOpen = Boolean(settingsAnchorEl); // Open state for settings menu

    const [drawerOpen, setDrawerOpen] = useState(false);  // Open state for navigation drawer
    const { t } = useLanguage();

    const dispatch = useDispatch();
    const router = useRouter();
    const savedVolume = useSelector(selectAlarmAudioVolume);

    const handleSettingsMenuOpen = (event: React.MouseEvent<HTMLButtonElement>) => {
        setSettingsAnchorEl(event.currentTarget);
    };

    const handleSettingsMenuClose = () => {
        setSettingsAnchorEl(null);
    };
    const [notificationsEnabled, setNotificationsEnabled] = useState<NotificationPermission>('default');

    useEffect(() => {
        console.log('[Navbar] Mounting');

        if ('Notification' in window) {
            setNotificationsEnabled(Notification.permission);
        }

        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js')
                .then((registration) => {
                    console.log('[PWA] Service Worker registered:', registration.scope);
                })
                .catch((error) => {
                    console.error('[PWA] Service Worker registration failed:', error);
                });

            const handleServiceWorkerMessage = (event: MessageEvent) => {
                if (event.data?.type === 'VIEW_ALARM' && event.data.eventData) {
                    const eventData = event.data.eventData;
                    dispatch(setEventPreview({ isOpen: true, eventData }));
                    dispatch(setSelectedRowId(eventData.id));
                    dispatch(setSelectedEvent(eventData));
                    dispatch(setEventData(eventData));
                    router.push('/event-details');
                }
            };

            navigator.serviceWorker.addEventListener('message', handleServiceWorkerMessage);
            return () => {
                navigator.serviceWorker.removeEventListener('message', handleServiceWorkerMessage);
            };
        } else {
            console.warn('[PWA] Service Worker not supported');
        }
    }, [dispatch, router]);

    // fix where u can actually turn notifications off
    const handleNotifications = async () => {
        if (!('Notification' in window)) {
            alert('Notifications are not supported in this browser');
            return;
        }

        if (Notification.permission === 'granted') {
            return;
        }

        const permission = await Notification.requestPermission();
        setNotificationsEnabled(permission);

        if (permission === 'granted') {
            new Notification('Notifications enabled', {
                body: 'You will now receive OSCAR notifications'
            });
        }
    }

    // Handle opening drawer
    const handleDrawerOpen = () => {
        setDrawerOpen(true);
    };

    // Handle closing drawer
    const handleDrawerClose = () => {
        setDrawerOpen(false);
    };

    const [volumeValue, setVolumeValue] = useState(savedVolume);

    const handleVolumeChange = (event: Event, newValue: number | number[]) => {
        const volume = newValue as number;
        setVolumeValue(volume);
        dispatch(setAlarmAudioVolume(volume));
    };

    // Menu items for drawer
    const menuItems = [
        {
            title: t('dashboard'),
            icon: <DashboardRoundedIcon/>,
            href: "/",
        },
        {
            title: t('events'),
            icon: <WarningRoundedIcon/>,
            href: "/event-log",
        },
        {
            title: t('map'),
            icon: <LocationOnRoundedIcon/>,
            href: "/map",
        },
        {
            title: t('national'),
            icon: <MediationIcon/>,
            href: "/national-view",
        },
        {
            title: t('reportGenerator'),
            icon: <InsertChart/>,
            href: "/report",
        },
    ]

    // Settings items for drawer
    const settingsItems = [
        {
            title: t('servers'),
            icon: <CloudRoundedIcon/>,
            href: "/servers",
        }
    ]

    // Drawer contents used for permanent and temporary variant
    const drawerContent = (
        <>
            <DrawerHeader>
                <IconButton onClick={handleDrawerClose} aria-label="close drawer">
                    <ChevronLeftIcon/>
                </IconButton>
            </DrawerHeader>
            <Divider/>
            <List>
                {menuItems.map((item) => (
                    <Link href={item.href} passHref key={item.title} onClick={!isDesktop ? handleDrawerClose : null}>
                        <ListItem disablePadding sx={{display: 'block'}}>
                            <ListItemButton
                                sx={{
                                    minHeight: 48,
                                    justifyContent: drawerOpen ? 'initial' : 'center',
                                    px: 2.5,
                                }}
                            >
                                <ListItemIcon
                                    sx={{
                                        minWidth: 0,
                                        mr: drawerOpen ? 3 : 'auto',
                                        justifyContent: 'center',
                                    }}
                                >
                                    {item.icon}
                                </ListItemIcon>
                                <ListItemText primary={item.title} sx={{opacity: drawerOpen ? 1 : 0}}/>
                            </ListItemButton>
                        </ListItem>
                    </Link>
                ))}
            </List>
            <Divider/>
            <List>
                {settingsItems.map((item) => (
                    <Link href={item.href} passHref key={item.title} onClick={!isDesktop ? handleDrawerClose : null}>
                        <ListItem disablePadding sx={{display: 'block'}}>
                            <ListItemButton
                                sx={{
                                    minHeight: 48,
                                    justifyContent: drawerOpen ? 'initial' : 'center',
                                    px: 2.5,
                                }}
                            >
                                <ListItemIcon
                                    sx={{
                                        minWidth: 0,
                                        mr: drawerOpen ? 3 : 'auto',
                                        justifyContent: 'center',
                                    }}
                                >
                                    {item.icon}
                                </ListItemIcon>
                                <ListItemText primary={item.title} sx={{opacity: drawerOpen ? 1 : 0}}/>
                            </ListItemButton>
                        </ListItem>
                    </Link>
                ))}
            </List>
            <Divider/>
        </>
    )

    return (
        <Box sx={{display: 'flex'}}>
            <CssBaseline/>
            <AppBar
                position="fixed"
                open={drawerOpen}
                elevation={0}
                sx={{
                    backgroundColor: "background.default",
                    color: "text.primary",
                    borderBottom: "solid",
                    borderColor: "action.selected"
                }}
                isDesktop={isDesktop}
            >
                <Toolbar>
                    <IconButton
                        color="inherit"
                        aria-label={t('openDrawer')}
                        onClick={handleDrawerOpen}
                        edge="start"
                        sx={{
                            marginRight: 5,
                            ...(drawerOpen && {display: 'none'}),
                        }}
                    >
                        <MenuIcon/>
                    </IconButton>
                    <Stack direction={"row"} width={"100%"} alignItems={"center"} justifyContent={"space-between"}>
                        <Typography variant="h6" noWrap component="div">
                            {t('appTitle')}
                        </Typography>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <LanguageSelector />
                            <Tooltip title={t('alarmVolume')} arrow placement="top">
                                <IconButton
                                    color="inherit"
                                    aria-label={t('openNotifications')}
                                    onClick={handleMenuOpen}
                                >
                                    {savedVolume === 0 ? <NotificationsOffIcon/> : <NotificationsRoundedIcon/>}
                                </IconButton>
                            </Tooltip>
                        </Stack>
                    </Stack>
                </Toolbar>
            </AppBar>
            <Menu
                id="settings-menu"
                anchorEl={settingsAnchorEl}
                open={settingsMenuOpen}
                onClose={handleSettingsMenuClose}
                MenuListProps={{
                    'aria-labelledby': 'settings-button',
                }}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
            >
                <MenuItem>
                    <Box sx={{ width: 200, padding: 1 }}>
                        <Typography variant="body2" gutterBottom>
                            {t('alarmVolume')}
                        </Typography>

                        <Stack spacing={2} direction="row" sx={{alignItems: 'center', mb: 1}}>
                            <VolumeDown/>
                            <Slider
                                aria-label={t('volume')}
                                value={volumeValue}
                                onChange={handleVolumeChange}
                                valueLabelDisplay="auto"
                                min={0}
                                max={100}
                            />
                            <VolumeUp/>
                        </Stack>
                    </Box>
                </MenuItem>
            </Menu>
            <Drawer variant="permanent" open={drawerOpen}>
                <DrawerHeader>
                    <IconButton onClick={handleDrawerClose}>
                        <ChevronLeftIcon/>
                    </IconButton>
                </DrawerHeader>
                <Divider/>
                <List>
                    {menuItems.map((item) => (
                        <Link href={item.href} passHref key={item.title}>
                            <ListItem disablePadding sx={{display: 'block'}}>
                                <ListItemButton
                                    sx={{
                                        minHeight: 48,
                                        justifyContent: drawerOpen ? 'initial' : 'center',
                                        px: 2.5,
                                    }}
                                >
                                    <ListItemIcon
                                        sx={{
                                            minWidth: 0,
                                            mr: drawerOpen ? 3 : 'auto',
                                            justifyContent: 'center',
                                        }}
                                    >
                                        {item.icon}
                                    </ListItemIcon>
                                    <ListItemText primary={item.title} sx={{opacity: drawerOpen ? 1 : 0}}/>
                                </ListItemButton>
                            </ListItem>
                        </Link>
                    ))}
                </List>
                <Divider/>
                <List>
                    {settingsItems.map((item) => (
                        <Link href={item.href} passHref key={item.title}>
                            <ListItem disablePadding sx={{display: 'block'}}>
                                <ListItemButton
                                    sx={{
                                        minHeight: 48,
                                        justifyContent: drawerOpen ? 'initial' : 'center',
                                        px: 2.5,
                                    }}
                                >
                                    <ListItemIcon
                                        sx={{
                                            minWidth: 0,
                                            mr: drawerOpen ? 3 : 'auto',
                                            justifyContent: 'center',
                                        }}
                                    >
                                        {item.icon}
                                    </ListItemIcon>
                                    <ListItemText primary={item.title} sx={{opacity: drawerOpen ? 1 : 0}}/>
                                </ListItemButton>
                            </ListItem>
                        </Link>
                    ))}
                </List>
                <Divider/>
            </Drawer>

            <Box sx={{display: "none"}}>
                <AlarmAudio/>
            </Box>
            <Box component="main" sx={{height: "100%", width: "100%", m: 2}}>
                <DrawerHeader/>
                {children}
            </Box>
        </Box>
    );
}
